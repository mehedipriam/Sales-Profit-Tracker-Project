// Small line icons for the figure cards (24x24, drawn with the current text colour).
const Svg = ({ children }) => (
  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"
       strokeLinecap="round" strokeLinejoin="round">{children}</svg>
)

export const TrendUpIcon = () => <Svg><path d="M22 7l-8.5 8.5-5-5L2 17" /><path d="M16 7h6v6" /></Svg>
export const BoxIcon = () => <Svg><path d="M21 8l-9-5-9 5 9 5 9-5z" /><path d="M3 8v8l9 5 9-5V8" /><path d="M12 13v8" /></Svg>
export const ChartIcon = () => <Svg><path d="M3 3v18h18" /><path d="M8 16v-4" /><path d="M13 16V9" /><path d="M18 16V6" /></Svg>
export const TruckIcon = () => (
  <Svg><path d="M1 4h14v12H1z" /><path d="M15 8h4l3 3v5h-7z" /><circle cx="5.5" cy="18.5" r="2" /><circle cx="18.5" cy="18.5" r="2" /></Svg>
)
export const ReceiptIcon = () => (
  <Svg><path d="M5 2v20l2.5-1.5L10 22l2-1.5 2 1.5 2.5-1.5L19 22V2l-2.5 1.5L14 2l-2 1.5L10 2 7.5 3.5z" /><path d="M9 8h6M9 12h6M9 16h4" /></Svg>
)
export const WalletIcon = () => (
  <Svg><path d="M20 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0 0 4h15v13H5a2 2 0 0 1-2-2V5" /><path d="M16 14h.01" /></Svg>
)
export const ClockIcon = () => <Svg><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></Svg>
export const ReturnIcon = () => <Svg><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5" /></Svg>
export const CancelIcon = () => <Svg><circle cx="12" cy="12" r="9" /><path d="M15 9l-6 6M9 9l6 6" /></Svg>
export const SunIcon = () => (
  <Svg><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></Svg>
)
export const MoonIcon = () => <Svg><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /></Svg>
export const EyeIcon = () => (
  <Svg><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" /><circle cx="12" cy="12" r="3" /></Svg>
)
export const EyeOffIcon = () => (
  <Svg><path d="M10.6 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-2.2 3.2M6.6 6.6C3.7 8.4 2 12 2 12s3.5 7 10 7a9.6 9.6 0 0 0 5.4-1.6" /><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" /><path d="M2 2l20 20" /></Svg>
)
export const PencilIcon = () => <Svg><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z" /></Svg>
export const TrashIcon = () => <Svg><path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M19 6l-1 14H6L5 6" /><path d="M10 11v5M14 11v5" /></Svg>
