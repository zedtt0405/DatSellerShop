import { Link } from 'react-router'
import CollectionSection from '../components/home/CollectionSection.tsx'
import GallerySection from '../components/home/GallerySection.tsx'
import HeroBanner from '../components/home/HeroBanner.tsx'
import ProductCard from '../components/home/ProductCard.tsx'
import { arrivalLooks } from '../data/homeImages.ts'

function HomePage() {
  return (
    <>
      <HeroBanner />

      <section className="mx-auto max-w-7xl px-4 py-14 md:px-6 md:py-20">
        <h2 className="text-center text-2xl font-semibold tracking-[0.18em] text-ink md:text-3xl">
          NEW ARRIVAL
        </h2>
        <p className="mx-auto mt-3 max-w-xl text-center text-sm leading-6 text-ash">
          Khung xem trước giao diện. Ảnh minh họa từ Unsplash, chưa phải sản
          phẩm, giá hay tồn kho của cửa hàng.
        </p>
        <div className="mt-10 grid grid-cols-2 gap-x-4 gap-y-8 lg:grid-cols-4">
          {arrivalLooks.map((look) => (
            <ProductCard
              key={look.photoId}
              src={look.src}
              alt={look.alt}
              title={look.title}
            />
          ))}
        </div>
        <div className="mt-10 text-center">
          <Link
            to="/shop"
            className="inline-flex min-h-11 items-center border border-ink px-6 text-sm font-semibold text-ink hover:bg-ink hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          >
            Xem tất cả
          </Link>
        </div>
      </section>

      <section className="bg-ash text-white">
        <div className="mx-auto grid max-w-7xl items-center gap-8 px-4 py-12 md:grid-cols-5 md:px-6 md:py-16">
          <img
            src="/images/home/featured.jpg"
            alt="Người mẫu kính tròn trước tường vàng"
            loading="lazy"
            className="aspect-[4/3] w-full object-cover md:col-span-3"
          />
          <div className="md:col-span-2">
            <span className="mb-5 block h-1 w-12 bg-neon" aria-hidden="true" />
            <h2 className="text-3xl font-semibold tracking-tight md:text-4xl">
              Đường phố, gọn và rõ
            </h2>
            <p className="mt-4 text-sm leading-6 text-white/85">
              Một hướng hình ảnh khác với banner đầu trang: màu đậm, chi tiết
              ít, dễ nhận trên mobile.
            </p>
            <Link
              to="/shop"
              className="mt-8 inline-flex min-h-11 items-center border border-white px-6 text-sm font-semibold text-white hover:border-neon hover:bg-neon hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neon"
            >
              Xem sản phẩm
            </Link>
          </div>
        </div>
      </section>

      <CollectionSection />
      <GallerySection />
    </>
  )
}

export default HomePage
