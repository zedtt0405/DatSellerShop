function SiteFooter() {
  return (
    <footer className="bg-ash text-white">
      <div className="mx-auto max-w-7xl px-4 py-10 md:px-6">
        <p className="flex items-center gap-2 text-lg font-semibold">
          DatSellerShop
          <span className="inline-block h-2 w-2 bg-neon" aria-hidden="true" />
        </p>
        <p className="mt-3 max-w-xl text-sm leading-6 text-white/85">
          Cửa hàng thời trang streetwear và tối giản. Giao diện khách hàng đang
          được xây dựng.
        </p>
        <p className="mt-6 text-sm text-white/70">
          © {new Date().getFullYear()} DatSellerShop. Đồ án chuyên ngành.
        </p>
      </div>
    </footer>
  )
}

export default SiteFooter
