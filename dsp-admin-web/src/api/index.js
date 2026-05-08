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
  approve: (transno, versionNo, data) => request.post(`/interface/${transno}/version/${versionNo}/approve`, data),
  reject: (transno, versionNo, data) => request.post(`/interface/${transno}/version/${versionNo}/reject`, data),
  offline: (transno) => request.post(`/interface/${transno}/offline`),
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
  pending: (params) => request.get('/interface/approval-pending', { params }),
  records: (transno, params) => request.get(`/interface/${transno}/approval-records`, { params }),
  submit: (transno, versionNo, data) => request.post(`/${transno}/version/${versionNo}/submit`, data),
  approve: (transno, versionNo, data) => request.post(`/${transno}/version/${versionNo}/approve`, data),
  reject: (transno, versionNo, data) => request.post(`/${transno}/version/${versionNo}/reject`, data)
}

// 审计日志API
export const auditApi = {
  list: (params) => request.get('/audit/list', { params })
}

// 接口数据源关联API
export const interfaceDatasourceApi = {
  list: (transno) => request.get(`/interface/${transno}/datasources`),
  bind: (transno, dsNames) => request.post(`/interface/${transno}/datasources`, { dsNames }),
  add: (transno, dsName) => request.post(`/interface/${transno}/datasource/${dsName}`),
  remove: (transno, dsName) => request.delete(`/interface/${transno}/datasource/${dsName}`)
}
