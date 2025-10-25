import React, { useState } from 'react';

function App() {
  const [endpoint, setEndpoint] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const createInstance = async () => {
    setLoading(true);
    setError('');
    setEndpoint('');
    try {
      const res = await fetch('http://localhost:8080/instances', { method: 'POST' });
      const data = await res.json();
      setEndpoint(data.endpoint || 'Error creating instance');
    } catch (e) {
      setError(e.message || 'Request failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ textAlign: 'center', marginTop: '50px', fontFamily: 'system-ui' }}>
      <button onClick={createInstance} disabled={loading}>
        {loading ? 'Creating…' : 'Create A'}
      </button>

      {endpoint && (
        <p style={{ marginTop: 20 }}>
          <strong>Endpoint:</strong> <code>{endpoint}</code>
        </p>
      )}

      {error && <p style={{ color: 'red' }}>Error: {error}</p>}
    </div>
  );
}

export default App;
