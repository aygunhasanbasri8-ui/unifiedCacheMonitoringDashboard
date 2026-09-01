import "./ProblemError.css";

// RFC 7807 hatasından türetilen { message, traceId } state'ini gösterir.
// className, çağıran sayfanın zaten sahip olduğu -error stiliyle uyumlu olsun diye dışarıdan verilir.
export default function ProblemError({ error, className }) {
  if (!error) return null;
  const message = typeof error === "string" ? error : error.message;
  const traceId = typeof error === "string" ? null : error.traceId;

  if (!message) return null;

  return (
    <div className={className}>
      {message}
      {traceId && <div className="problem-error-trace">Destek referansı: {traceId}</div>}
    </div>
  );
}
