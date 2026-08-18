type Props = {
  name:
    | 'home' | 'bolt' | 'db' | 'shield' | 'bill' | 'logout' | 'user' | 'doc'
    | 'panel-left' | 'panel-right'
    | 'postgres' | 'mysql' | 'h2'
    | 'spring' | 'docker' | 'swagger' | 'jdbc' | 'jwt' | 'oauth' | 'nginx'
  size?: number
}
export function Icon({ name, size = 18 }: Props){
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill:'none', xmlns:'http://www.w3.org/2000/svg' as const }
  switch(name){
    case 'home': return (
      <svg {...common}><path d="M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1v-8.5Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/></svg>
    )
    case 'bolt': return (
      <svg {...common}><path d="M13 2 3 14h8l-1 8 10-12h-8l1-8Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/></svg>
    )
    case 'db': return (
      <svg {...common}><path d="M4 6c0 1.66 3.58 3 8 3s8-1.34 8-3-3.58-3-8-3-8 1.34-8 3Z" stroke="currentColor" strokeWidth="1.8"/><path d="M4 6v6c0 1.66 3.58 3 8 3s8-1.34 8-3V6" stroke="currentColor" strokeWidth="1.8"/><path d="M4 12v6c0 1.66 3.58 3 8 3s8-1.34 8-3v-6" stroke="currentColor" strokeWidth="1.8"/></svg>
    )
    case 'shield': return (
      <svg {...common}><path d="M12 2 20 6v6c0 5-3.5 9.5-8 10-4.5-.5-8-5-8-10V6l8-4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/><path d="M9 12l2 2 4-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
    )
    case 'bill': return (
      <svg {...common}><path d="M7 3h10a2 2 0 0 1 2 2v16l-2-1-2 1-2-1-2 1-2-1-2 1V5a2 2 0 0 1 2-2Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/><path d="M9 8h6M9 12h6M9 16h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
    )
    case 'logout': return (
      <svg {...common}><path d="M10 17H6a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h4" stroke="currentColor" strokeWidth="1.8"/><path d="M15 7l5 5-5 5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><path d="M20 12H10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
    )
    case 'doc': return (
      <svg {...common}><path d="M6 3h9l3 3v15H6V3Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/><path d="M15 3v4h4M9 11h6M9 15h6M9 18h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
    )
    case 'user': return (
      <svg {...common}><path d="M20 21a8 8 0 1 0-16 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z" stroke="currentColor" strokeWidth="1.8"/></svg>
    )
    case 'panel-left': return (
      <svg {...common}><rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M9 4v16M14 9l-3 3 3 3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
    )
    case 'panel-right': return (
      <svg {...common}><rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M15 4v16M10 9l3 3-3 3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
    )
    case 'postgres': return (
      <svg {...common}><path d="M7 8.5c0-2.5 2.4-4.5 5.5-4.5S18 6 18 8.5V16c0 2.2-1.8 4-4 4h-1.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M7 13c1.2.9 2.8 1.4 4.5 1.4S14.8 13.9 16 13" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M8 18c1-.8 1.5-2 1.5-3.2V9.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="10" cy="8.5" r="0.8" fill="currentColor"/><circle cx="15" cy="8.5" r="0.8" fill="currentColor"/></svg>
    )
    case 'mysql': return (
      <svg {...common}><path d="M7 18c2.5-3.3 6.4-5.4 10.6-5.7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M9.5 10.5c.4-1.8 1.7-3.6 3.6-4.9 1.1-.7 2.3-1.1 3.5-1.1-.7 1-1.1 2.1-1.1 3.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M5.5 13.5c.8-.6 1.9-.9 3-.9 1.8 0 3.4.8 4.4 2.1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M7 8.5c-.8 1-.9 2.2-.2 3.4M15.5 14.5l2 3.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
    )
    case 'h2': return (
      <svg {...common}><path d="M5 6v12M19 6v12M5 12h14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M8 9v6M12 9l4 6M16 9v6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
    )
    case 'spring': return (
      <svg {...common}><path d="M5 14c4.5-6 10-7.8 14-8-1 4.7-3.7 10.8-10.5 12.2-2.2.5-4-.3-4.5-2.1-.4-1.1-.1-1.8 1-2.1 2-.6 4.9-.6 8-2.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
    )
    case 'docker': return (
      <svg {...common}><rect x="4" y="10" width="3" height="3" rx=".6" stroke="currentColor" strokeWidth="1.6"/><rect x="8" y="10" width="3" height="3" rx=".6" stroke="currentColor" strokeWidth="1.6"/><rect x="12" y="10" width="3" height="3" rx=".6" stroke="currentColor" strokeWidth="1.6"/><rect x="8" y="6" width="3" height="3" rx=".6" stroke="currentColor" strokeWidth="1.6"/><path d="M4 14h13.2a2.8 2.8 0 0 0 2.8-2.8V10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M5 18c1.4 1.2 3.2 2 5.2 2 3.8 0 7-2.6 8-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
    )
    case 'swagger': return (
      <svg {...common}><path d="M12 3v4M12 17v4M4.2 8l3.4 2M16.4 14l3.4 2M4.2 16l3.4-2M16.4 10l3.4-2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="12" cy="12" r="3.5" stroke="currentColor" strokeWidth="1.8"/></svg>
    )
    case 'jdbc': return (
      <svg {...common}><path d="M5 8h14M5 12h9M5 16h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M17 14v4M15 16h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
    )
    case 'jwt': return (
      <svg {...common}><path d="M12 3 5 7v5c0 4.4 2.7 7.8 7 9 4.3-1.2 7-4.6 7-9V7l-7-4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/><path d="m9 12 2 2 4-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
    )
    case 'oauth': return (
      <svg {...common}><circle cx="8" cy="12" r="3" stroke="currentColor" strokeWidth="1.8"/><circle cx="16" cy="12" r="3" stroke="currentColor" strokeWidth="1.8"/><path d="M11 12h2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
    )
    case 'nginx': return (
      <svg {...common}><path d="M12 3 19 7v10l-7 4-7-4V7l7-4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/><path d="M9 15V9l6 6V9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
    )
  }
}
