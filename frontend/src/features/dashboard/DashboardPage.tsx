import { useState, useEffect } from 'react';
import { useAuth } from '../auth/AuthContext';

export function DashboardPage() {
    const { user, tokenExpiresAt, logout } = useAuth();

    // useState è come un campo di classe in Java, ma quando cambia
    // React ridisegna automaticamente il componente.
    // useState(0) → valore iniziale 0
    // [secondi, setSecondi] → getter e setter
    const [secondsRemaining, setSecondsRemaining] = useState<number>(0);

    // useEffect è il ciclo di vita del componente.
    // Viene eseguito dopo ogni render, ma solo quando cambiano
    // le dipendenze nell'array finale [tokenExpiresAt].
    // È simile a @PostConstruct in Spring, ma più flessibile.
    useEffect(() => {
        if (!tokenExpiresAt) return;

        // Calcola subito i secondi rimanenti al primo render
        const calc = () => {
            const diff = Math.max(
                0,
                Math.floor((tokenExpiresAt.getTime() - Date.now()) / 1000)
            );
            setSecondsRemaining(diff);
        };

        calc(); // esegui subito

        // setInterval chiama calc() ogni secondo (1000ms)
        // È come un thread che gira in background
        const interval = setInterval(calc, 1000);

        // La funzione di return è il "cleanup" — viene chiamata
        // quando il componente viene smontato o prima del prossimo
        // useEffect. Ferma l'intervallo per evitare memory leak.
        // Equivalente di destroy() o @PreDestroy in Spring.
        return () => clearInterval(interval);

    }, [tokenExpiresAt]); // riesegue solo quando tokenExpiresAt cambia

    // Formatta i secondi come "MM:SS"
    // padStart(2, '0') aggiunge uno zero iniziale se serve: 9 → "09"
    function formatCountdown(seconds: number): string {
        const m = Math.floor(seconds / 60);
        const s = seconds % 60;
        return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    }

    // Colore del countdown in base al tempo rimanente
    function countdownColor(): string {
        if (secondsRemaining > 300) return 'text-green-600';  // > 5 min
        if (secondsRemaining > 60)  return 'text-yellow-600'; // > 1 min
        return 'text-red-600';                                 // < 1 min
    }

    return (
        <div className="min-h-screen bg-gray-50 p-8">
            <div className="max-w-2xl mx-auto space-y-6">

                {/* Header */}
                <div className="flex justify-between items-center">
                    <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
                    <button
                        onClick={logout}
                        className="text-sm text-gray-500 hover:text-gray-700"
                    >
                        Esci
                    </button>
                </div>

                {/* Info utente */}
                <div className="bg-white rounded-xl shadow-sm p-6">
                    <p className="text-gray-700">
                        Benvenuto, <span className="font-medium">{user?.email}</span>
                    </p>
                    <span className="inline-block mt-2 px-3 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-700">
            {user?.role}
          </span>
                </div>

                {/* Token countdown */}
                <div className="bg-white rounded-xl shadow-sm p-6">
                    <p className="text-sm text-gray-500 mb-1">Access token scade in</p>
                    <p className={`text-3xl font-mono font-bold ${countdownColor()}`}>
                        {secondsRemaining > 0 ? formatCountdown(secondsRemaining) : 'Scaduto'}
                    </p>
                    <p className="text-xs text-gray-400 mt-2">
                        The token is automatically renewed via the refresh token
                    </p>
                </div>

                {/* Sezione visibile solo agli ADMIN */}
                {user?.role === 'ADMIN' && (
                    <div className="bg-blue-50 border border-blue-200 rounded-xl p-6">
                        <p className="text-sm font-medium text-blue-800 mb-1">
                            Admin area
                        </p>
                        <p className="text-sm text-blue-600">
                            This section is only visible to users with the ADMIN role.
                            USER users cannot see it.
                        </p>
                    </div>
                )}

                {/* Sezione visibile a tutti */}
                <div className="bg-white rounded-xl shadow-sm p-6">
                    <p className="text-sm font-medium text-gray-700 mb-1">
                        Area comune
                    </p>
                    <p className="text-sm text-gray-500">
                        This section is visible to all authenticated users.
                    </p>
                </div>

            </div>
        </div>
    );
}