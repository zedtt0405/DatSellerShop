import { Link } from 'react-router'

function NotFoundPage() {
  return (
    <section className="mx-auto max-w-5xl px-4 py-12 md:px-6 md:py-16">
      <p className="mb-3 text-sm font-semibold tracking-[0.18em] text-ash uppercase">
        404
      </p>
      <h1 className="mb-4 text-3xl font-semibold tracking-tight text-ink md:text-4xl">
        Không tìm thấy trang
      </h1>
      <p className="mb-8 max-w-xl text-base leading-7 text-ash">
        Đường dẫn này không tồn tại trong DatSellerShop.
      </p>
      <Link
        to="/"
        className="inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink hover:bg-neon/80 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
      >
        Về trang chủ
      </Link>
    </section>
  )
}

export default NotFoundPage
