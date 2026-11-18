import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { AuthProvider, ToastProvider } from './context';
import { MotionProvider } from './components/ui/MotionProvider';
import './styles.css';
import './styles/auth.css';
import './styles/email-security.css';
import './styles/landing.css';
import './styles/account.css';
import './styles/night.css';
import './styles/magic-night.css';
import './styles/quizzes.css';
import { BrandAssetsProvider } from './components/brand/BrandAssetsProvider';

document.documentElement.dataset.reducedMotion = localStorage.getItem('quiz-reduced-motion') ?? 'false';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode><BrandAssetsProvider><MotionProvider><BrowserRouter><ToastProvider><AuthProvider><App/></AuthProvider></ToastProvider></BrowserRouter></MotionProvider></BrandAssetsProvider></React.StrictMode>,
);
