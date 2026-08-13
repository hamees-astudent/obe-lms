import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';

/**
 * Application shell.
 *
 * The sidebar is a permanent column from `lg` up and an off-canvas drawer below
 * it. Previously it was a fixed 240px column at every width, which on a phone
 * left roughly a third of the screen for the page itself.
 */
export default function AppLayout() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const location = useLocation();

  // Navigating away should not leave the drawer covering the new page.
  useEffect(() => setDrawerOpen(false), [location.pathname]);

  // Esc closes the drawer, matching every other overlay in the app.
  useEffect(() => {
    if (!drawerOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setDrawerOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [drawerOpen]);

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      {/* Permanent column at lg+ */}
      <div className="hidden lg:flex">
        <Sidebar />
      </div>

      {/* Drawer below lg */}
      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label="Close navigation"
            onClick={() => setDrawerOpen(false)}
            className="absolute inset-0 bg-black/40 animate-fade-in"
          />
          <div className="relative flex h-full w-64 max-w-[80vw]">
            <Sidebar onNavigate={() => setDrawerOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex flex-1 flex-col overflow-hidden">
        <Header onOpenNav={() => setDrawerOpen(true)} />
        <main className="flex-1 overflow-y-auto p-4 sm:p-6 scrollbar-thin animate-fade-in">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
