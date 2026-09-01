import axios from "axios";
import { parseProblem } from "./problemDetails";

const client = axios.create({
  baseURL: "http://localhost:8080/api",
});

// Her isteğe otomatik token ekle (varsa) ve backend log'unda izlenebilmesi için trace kimliği ekle
client.interceptors.request.use((config) => {
  const token = localStorage.getItem("ucmd_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers["X-Request-Id"] = crypto.randomUUID();
  return config;
});

// RFC 7807 (application/problem+json) hata gövdesini merkezi olarak ayrıştırır;
// çağıran kod err.problem üzerinden kullanıcıya gösterilecek mesaja ve traceId'ye erişir.
client.interceptors.response.use(
  (response) => response,
  (error) => {
    error.problem = parseProblem(error);
    return Promise.reject(error);
  }
);

export default client;
