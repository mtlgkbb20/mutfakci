import axios from "axios";
import { withAuthHeader } from "./lib";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1",
});

api.interceptors.request.use((config) => withAuthHeader(config, localStorage));
