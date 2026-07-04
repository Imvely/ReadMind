import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
// HashRouter: 정적 호스팅(HF Space 등)엔 SPA rewrite가 없어 /reader/3 새로고침이 404가 된다.
// 해시 라우팅은 서버 설정 없이 딥링크·새로고침이 동작한다(§8 P0 베타 배포).
import { HashRouter } from 'react-router-dom';
import App from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <HashRouter>
        <App />
      </HashRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
