import { Outlet } from 'react-router'
import SiteFooter from '../components/SiteFooter.tsx'
import SiteHeader from '../components/SiteHeader.tsx'

function MainLayout() {
  return (
    <div className="flex min-h-svh flex-col bg-white text-ink">
      <SiteHeader />
      <main className="w-full flex-1">
        <Outlet />
      </main>
      <SiteFooter />
    </div>
  )
}

export default MainLayout
