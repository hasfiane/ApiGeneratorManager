import brandLogo from '../assets/brand-logo.png'

export function BrandLogo({
  className = 'brandLogo brandLogo--default',
  alt = 'Api Generator',
}: {
  readonly className?: string
  readonly alt?: string
}) {
  return (
    <span className={className.startsWith('brandLogo') ? className : `brandLogo ${className}`}>
      <img className="brandLogoImage" src={brandLogo} alt={alt} />
    </span>
  )
}
