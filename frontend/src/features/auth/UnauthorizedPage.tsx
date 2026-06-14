// src/features/auth/UnauthorizedPage.tsx
import { useNavigate } from 'react-router-dom';

export function UnauthorizedPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">Access denied</h1>
        <p className="text-gray-500 text-sm mb-6">
          You don't have permission to view this page
        </p>
        <button
          onClick={() => navigate('/dashboard')}
          className="text-blue-600 text-sm hover:underline"
        >
          Back to dashboard
        </button>
      </div>
    </div>
  );
}