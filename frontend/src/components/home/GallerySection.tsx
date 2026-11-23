import { galleryLooks } from '../../data/homeImages.ts'

function GallerySection() {
  return (
    <section className="mx-auto max-w-7xl px-4 py-14 md:px-6 md:py-20">
      <h2 className="mb-8 text-center text-2xl font-semibold tracking-[0.18em] text-ink md:text-3xl">
        GALLERY
      </h2>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
        {galleryLooks.map((look) => (
          <img
            key={look.photoId}
            src={look.src}
            alt={look.alt}
            loading="lazy"
            className="aspect-square w-full object-cover transition duration-300 hover:opacity-90"
          />
        ))}
      </div>
    </section>
  )
}

export default GallerySection
