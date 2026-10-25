export type LauncherVersion = {
  id: number;
  version: string;
  downloadUrl: string;
  sha256?: string;
  description?: string;
  forceUpdate?: number;
  enabled?: number;
  createTime?: string;
};
