type ProductCardProps = {
  src: string
  alt: string
  title: string
}

function ProductCard({ src, alt, title }: ProductCardProps) {
  return (
    <article>
      <div className="overflow-hidden bg-line">
        <img
          src={src}
          alt={alt}
          loading="lazy"
          className="aspect-[3/4] w-full object-cover transition duration-300 hover:scale-[1.03]"
        />
      </div>
      <div className="mt-3 flex items-start justify-between gap-2">
        <h3 className="text-sm font-medium tracking-wide text-ink uppercase">{title}</h3>
        <span className="shrink-0 bg-sun px-2 py-0.5 text-[11px] font-semibold text-ink">
          Minh họa
        </span>
      </div>
      <p className="mt-1 text-xs leading-5 text-ash">Chưa nối dữ liệu sản phẩm</p>
    </article>
  )
}

export default ProductCard
