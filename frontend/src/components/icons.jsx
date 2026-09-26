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
