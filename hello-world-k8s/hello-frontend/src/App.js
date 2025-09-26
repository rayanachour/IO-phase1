import React, { useState } from 'react';

function App() {
  const [endpoint, setEndpoint] = useState('');

  const createInstance = async () => {
    const res = await fetch('http://localhost:8080/instances', { method: 'POST' });
    const data = await res.json();
    setEndpoint(data.endpoint || 'Error creating instance');
  };

  return (
    <div style={{ textAlign: 'center', marginTop: '50px' }}>
      <button onClick={createInstance}>Create A</button>
      {endpoint && <p>endpoint: {endpoint}</p>}
    </div>
  );
}

export default App;
