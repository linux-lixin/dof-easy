export const ApiGlobalConfig = {
    imageViewer: {
        baseURL: (import.meta.env.VITE_API_BASE_URL || '/api') + '/common/previewMinio/'
    }
};
