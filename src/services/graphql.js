import request from '../utils/request';

export async function query(namespace, playload) {
  return request(`/api/${namespace}`, {
    method: 'POST',
    body: playload,
  });
}
