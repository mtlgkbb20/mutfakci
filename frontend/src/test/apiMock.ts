import { vi } from "vitest";

type Handler = (url: string, body?: unknown) => unknown;

/** Routes mocked axios calls by URL; an unrouted URL rejects so missing stubs fail loudly. */
export function routeApi(api: { get: unknown; post: unknown }, routes: { get?: Record<string, Handler>; post?: Record<string, Handler> }) {
  const resolve = (table: Record<string, Handler> | undefined, url: string, body?: unknown) => {
    const handler = table?.[url];
    if (!handler) return Promise.reject(Object.assign(new Error(`unrouted ${url}`), { response: { status: 404 } }));
    try {
      const result = handler(url, body);
      return result instanceof Promise ? result.then((data) => ({ data })) : Promise.resolve({ data: result });
    } catch (error) {
      return Promise.reject(error);
    }
  };
  vi.mocked(api.get as (url: string) => Promise<unknown>).mockImplementation((url: string) => resolve(routes.get, url));
  vi.mocked(api.post as (url: string, body?: unknown) => Promise<unknown>).mockImplementation((url: string, body?: unknown) => resolve(routes.post, url, body));
}

export const httpError = (status: number) => Object.assign(new Error(`HTTP ${status}`), { response: { status } });

export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}
