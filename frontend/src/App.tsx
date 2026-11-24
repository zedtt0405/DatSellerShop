import { Route, Routes } from 'react-router'
import MainLayout from './layouts/MainLayout.tsx'
import HomePage from './pages/HomePage.tsx'
import CartPage from './pages/CartPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import ProductDetailPage from './pages/ProductDetailPage.tsx'
import RegisterPage from './pages/RegisterPage.tsx'
import ShopPage from './pages/ShopPage.tsx'

function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/shop" element={<ShopPage />} />
        <Route path="/shop/:slug" element={<ProductDetailPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
