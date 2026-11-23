import { Link } from 'react-router'

function HeroBanner() {
  return (
    <section className="bg-white">
      <div className="mx-auto grid max-w-7xl items-center gap-8 px-4 py-8 md:grid-cols-2 md:gap-12 md:px-6 md:py-14">
        <div className="order-2 md:order-1">
          <p className="text-xs font-semibold tracking-[0.22em] text-ash uppercase">
            Streetwear · Minimal
          </p>
          <h1 className="mt-3 text-4xl font-semibold tracking-tight text-ink sm:text-5xl lg:text-6xl">
            DatSellerShop
          </h1>
          <p className="mt-4 max-w-md text-base leading-7 text-ash">
            Form gọn, màu rõ, dễ phối mỗi ngày. Đây là giao diện giới thiệu
            thương hiệu, catalog sẽ được nối sau.
          </p>
          <Link
            to="/shop"
            className="mt-8 inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink hover:bg-neon/80 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          >
            Khám phá ngay
          </Link>
        </div>
        <img
          src="/images/home/hero.jpg"
          alt="Người mẫu mặc set hoodie vàng, phong cách streetwear"
          className="order-1 aspect-[4/5] w-full rounded-tl-[4.5rem] rounded-br-[4.5rem] object-cover object-top md:order-2 md:aspect-[3/4]"
        />
      </div>
    </section>
  )
}

export default HeroBanner
