import request from './request'

// 认证API
export const authApi = {
  login: (data) => request.post('/login', data)
}

// 接口管理API
export const interfaceApi = {
  list: (params) => request.get('/interface/list', { params }),
  detail: (id) => request.get(`/interface/${id}`),
  create: (data) => request.post('/interface', data),
  update: (id, data) => request.put(`/interface/${id}`, data),
  delete: (id) => request.delete(`/interface/${id}`),
  saveSchema: (transno, data) => request.post(`/interface/${transno}/version`, data),
  versions: (transno, params) => request.get(`/interface/${transno}/versions`, { params }),
  getVersion: (transno, versionNo) => request.get(`/interface/${transno}/version/${versionNo}`),
  submitApproval: (transno, versionNo, data) => request.post(`/interface/${transno}/version/${versionNo}/submit`, data),
  getLatestVersion: (transno) => request.get(`/interface/${transno}/version/latest`),
  approve: (transno) => request.post(`/interface/${transno}/approve`),
  reject: (transno, data) => request.post(`/interface/${transno}/reject`, data),
  offline: (transno) => request.post(`/interface/${transno}/offline`),
  withdraw: (transno) => request.post(`/interface/${transno}/withdraw`),
  debug: (data) => request.post('/interface/debug', data)
}

// XML模板管理API
export const templateApi = {
  list: (params) => request.get('/template/list', { params }),
  detail: (id) => request.get(`/template/${id}`),
  create: (data) => request.post('/template', data),
  update: (id, data) => request.put(`/template/${id}`, data),
  delete: (id) => request.delete(`/template/${id}`),
  publish: (id, data) => request.post(`/template/${id}/publish`, data),
  offline: (id) => request.post(`/template/${id}/offline`),
  generate: (transno) => request.get('/template/generate', { params: { transno } }),
  history: (id, params) => request.get(`/template/${id}/history`, { params }),
  historyByTransno: (transno) => request.get(`/template/transno/${transno}/history`)
}

// 数据源管理API
export const datasourceApi = {
  list: (params) => request.get('/datasource/list', { params }),
  detail: (id) => request.get(`/datasource/${id}`),
  create: (data) => request.post('/datasource', data),
  update: (id, data) => request.put(`/datasource/${id}`, data),
  delete: (id) => request.delete(`/datasource/${id}`),
  test: (data) => request.post('/datasource/test', data)
}

// 应用授权API
export const appAuthApi = {
  list: () => request.get('/app/list'),
  detail: (id) => request.get(`/app/${id}`),
  create: (data) => request.post('/app', data),
  update: (id, data) => request.put(`/app/${id}`, data),
  delete: (id) => request.delete(`/app/${id}`),
  generateToken: (appId) => request.post(`/app/${appId}/token`)
}

// 导出任务API
export const exportApi = {
  list: (params) => request.get('/export/list', { params }),
  detail: (id) => request.get(`/export/${id}`)
}

// 审批管理API
export const approvalApi = {
  mySubmissions: (params) => request.get('/approval/my-submissions', { params }),
  pending: (params) => request.get('/approval/pending', { params }),
  history: (params) => request.get('/approval/history', { params }),
  submitApply: (data) => request.post('/approval/submit', data),
  approve: (id) => request.post(`/approval/${id}/approve`),
  reject: (id, data) => request.post(`/approval/${id}/reject`, data),
  withdraw: (id) => request.post(`/approval/${id}/withdraw`),
  flowDetail: (id) => request.get(`/approval/${id}/flow`),
  detail: (id) => request.get(`/approval/${id}/detail`),
}

// 接口关系API
export const relationApi = {
  provider: (params) => request.get('/relation/provider', { params }),
  applicant: (params) => request.get('/relation/applicant', { params }),
  offline: (id, data) => request.post(`/relation/${id}/offline`, data),
}

// 审计日志API
export const auditApi = {
  list: (params) => request.get('/audit/list', { params })
}

// 用户管理API
export const userApi = {
  list: (params) => request.get('/user/list', { params }),
  detail: (id) => request.get(`/user/${id}`),
  create: (data) => request.post('/user', data),
  update: (id, data) => request.put(`/user/${id}`, data),
  resetPassword: (id, password) => request.put(`/user/${id}/password`, { password }),
  updateStatus: (id, status) => request.put(`/user/${id}/status`, { status }),
  delete: (id) => request.delete(`/user/${id}`),
  assignRoles: (id, roleIds) => request.post(`/user/${id}/roles`, { roleIds }),
  profile: () => request.get('/user/profile'),
  updateProfile: (data) => request.put('/user/profile', data),
  changePassword: (data) => request.put('/user/change-password', data),
}

// 部门管理API
export const deptApi = {
  tree: () => request.get('/dept/tree'),
  create: (data) => request.post('/dept', data),
  update: (id, data) => request.put(`/dept/${id}`, data),
  delete: (id) => request.delete(`/dept/${id}`)
}

// 角色管理API
export const roleApi = {
  list: () => request.get('/role/list')
}

// 系统管理API
export const systemApi = {
  list: (params) => request.get('/system/list', { params }),
  create: (data) => request.post('/system', data),
  update: (id, data) => request.put(`/system/${id}`, data),
  delete: (id) => request.delete(`/system/${id}`)
}

// 配置导入导出API
export const configApi = {
  exportSingle: (transno) => request.get('/config/export', { params: { transno } }),
  exportBatch: (transnos) => request.post('/config/export/batch', { transnos }),
  importConfig: (data) => request.post('/config/import', data)
}


