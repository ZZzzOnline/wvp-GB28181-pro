import defaultSettings from '@/settings'

const title = defaultSettings.title || '益可达轨道巡检系统'

export default function getPageTitle(pageTitle) {
  if (pageTitle) {
    return `${pageTitle} - ${title}`
  }
  return `${title}`
}
