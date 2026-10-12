import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { keycloak } from './auth.ts'

// keycloak-js allows one init per instance, so it runs here rather than in an effect.
await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256' })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
