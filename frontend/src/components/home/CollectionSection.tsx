import { styleLooks } from '../../data/homeImages.ts'

function CollectionSection() {
  return (
    <section className="mx-auto max-w-7xl px-4 py-14 md:px-6 md:py-20">
      <div className="mb-8 max-w-2xl">
        <h2 className="text-2xl font-semibold tracking-tight text-ink md:text-3xl">
          Phong cách
        </h2>
        <p className="mt-3 text-sm leading-6 text-ash">
          Bốn hướng hình ảnh của DatSellerShop. Đây là phần giới thiệu phong
          cách, chưa phải danh mục sản phẩm từ Backend.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-12">
        {styleLooks.map((look, index) => (
          <article
            key={look.photoId}
            className={index % 2 === 0 ? 'md:col-span-7' : 'md:col-span-5'}
          >
            <div className="relative overflow-hidden">
              <img
                src={look.src}
                alt={look.alt}
                loading="lazy"
                className="aspect-[4/5] w-full object-cover md:aspect-[5/4]"
              />
              <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-ink/75 to-transparent p-4">
                <h3 className="text-lg font-semibold tracking-wide text-white uppercase">
                  {look.title}
                </h3>
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

export default CollectionSection
