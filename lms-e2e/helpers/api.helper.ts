import { APIRequestContext } from '@playwright/test';

const API_BASE = process.env.API_BASE_URL ?? 'http://localhost:8080/api';

/**
 * Thin typed wrapper around Playwright's APIRequestContext.
 * Automatically sets Content-Type and injects the Bearer token when provided.
 */
export class ApiHelper {
  constructor(private readonly request: APIRequestContext) {}

  async get<T>(path: string, token?: string): Promise<T> {
    const response = await this.request.get(`${API_BASE}${path}`, {
      headers: this.headers(token),
    });
    this.assertOk('GET', path, response.status());
    return response.json() as Promise<T>;
  }

  async post<T>(path: string, body: unknown, token?: string): Promise<T> {
    const response = await this.request.post(`${API_BASE}${path}`, {
      data: body,
      headers: this.headers(token),
    });
    this.assertOk('POST', path, response.status());
    return response.json() as Promise<T>;
  }

  async put<T>(path: string, body: unknown, token?: string): Promise<T> {
    const response = await this.request.put(`${API_BASE}${path}`, {
      data: body,
      headers: this.headers(token),
    });
    this.assertOk('PUT', path, response.status());
    return response.json() as Promise<T>;
  }

  async delete(path: string, token?: string): Promise<void> {
    const response = await this.request.delete(`${API_BASE}${path}`, {
      headers: this.headers(token),
    });
    this.assertOk('DELETE', path, response.status());
  }

  private headers(token?: string): Record<string, string> {
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) h['Authorization'] = `Bearer ${token}`;
    return h;
  }

  private assertOk(method: string, path: string, status: number): void {
    if (status < 200 || status >= 300) {
      throw new Error(`${method} ${path} returned unexpected status ${status}`);
    }
  }
}
