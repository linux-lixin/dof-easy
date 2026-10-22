// 统一维护 legacy 页面路由映射，方便 Header/menu 复用
export const legacyRoutes = {
  home: '/home',
  aboutClan: '/about-clan',
  aboutMe: '/about-me',
  faq: '/faq',
  gallery: '/gallery',
  matches: '/matches',
  matchSingle: '/matches-single',
  players: '/players',
  blog: '/blog',
  blogSingle: '/blog-single',
  contact: '/contact',
  index2: '/index-2'
} as const;
