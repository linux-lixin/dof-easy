import Request from './Request';
import type { LauncherConfig } from './entitys/LauncherConfig';
import type { LauncherVersion } from './entitys/LauncherVersion';
import type { LauncherBanner } from './entitys/LauncherBanner';
import type { PageResult } from './entitys/PageResult';

export const LauncherApi = {
  getConfig() {
    return Request.get<LauncherConfig>('/api/v1/admin/launcher/config');
  },
  saveConfig(data: LauncherConfig) {
    return Request.put<LauncherConfig>('/api/v1/admin/launcher/config', data);
  },

  listVersions(params: { keyword?: string; enabled?: number; page: number; pageSize: number }) {
    const q = new URLSearchParams();
    if (params.keyword) q.append('keyword', params.keyword);
    if (params.enabled != null) q.append('enabled', String(params.enabled));
    q.append('page', String(params.page));
    q.append('pageSize', String(params.pageSize));
    return Request.get<PageResult<LauncherVersion>>(`/api/v1/admin/launcher/versions?${q.toString()}`);
  },
  addVersion(data: Partial<LauncherVersion>) {
    return Request.post<LauncherVersion>('/api/v1/admin/launcher/versions', data);
  },
  updateVersion(data: LauncherVersion) {
    return Request.put<LauncherVersion>('/api/v1/admin/launcher/versions', data);
  },
  deleteVersion(id: number) {
    return Request.delete(`/api/v1/admin/launcher/versions/${id}`);
  },

  listBanners(params?: { enabled?: number }) {
    const q = new URLSearchParams();
    if (params?.enabled != null) q.append('enabled', String(params.enabled));
    const qs = q.toString();
    return Request.get<LauncherBanner[]>(`/api/v1/admin/launcher/banners${qs ? `?${qs}` : ''}`);
  },
  addBanner(data: Partial<LauncherBanner>) {
    return Request.post<LauncherBanner>('/api/v1/admin/launcher/banners', data);
  },
  updateBanner(data: LauncherBanner) {
    return Request.put<LauncherBanner>('/api/v1/admin/launcher/banners', data);
  },
  deleteBanner(id: number) {
    return Request.delete(`/api/v1/admin/launcher/banners/${id}`);
  }
};
