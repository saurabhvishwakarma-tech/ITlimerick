import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../api/http';
import type { CheckinResult, CheckinSummary, EventView, SessionFeedbackView } from '../../api/types';
import { Empty, ErrorState, Loading } from '../../components/ui';
import { formatDateTime, formatTime } from '../../lib/time';
import { Stars } from '../AttendeeExtras';

type Outcome = { kind: 'ok' | 'again' | 'error'; title: string; detail?: string };

// ---------------- Check-in ----------------

export function CheckinTab({ base, event }: { base: string; event: EventView }) {
  const client = useQueryClient();
  const list = useQuery({
    queryKey: ['admin', `${base}/checkins`],
    queryFn: () => api<CheckinSummary>(`${base}/checkins`),
    refetchInterval: 15_000,
  });
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [code, setCode] = useState('');
  const [email, setEmail] = useState('');
  const [filter, setFilter] = useState('');
  const busy = useRef(false);

  const show = useCallback(
    (r: CheckinResult) => {
      setOutcome(
        r.alreadyCheckedIn
          ? { kind: 'again', title: `${r.name} is already checked in`, detail: `Since ${formatTime(r.checkedInAt, event.timezone)}` }
          : { kind: 'ok', title: `${r.name} is checked in`, detail: r.email },
      );
      try {
        navigator.vibrate?.(r.alreadyCheckedIn ? [80, 60, 80] : 120);
      } catch {
        // not supported
      }
      client.invalidateQueries({ queryKey: ['admin', `${base}/checkins`] });
    },
    [base, client, event.timezone],
  );

  const fail = (e: unknown, what: string) => {
    const notFound = e instanceof ApiError && e.status === 404;
    setOutcome({
      kind: 'error',
      title: notFound ? `No ${what} found` : 'Check-in failed',
      detail: notFound
        ? what === 'ticket'
          ? 'This code is not a ticket for this event. Ask them to open Ticket in the app, or check them in by email.'
          : 'Nobody has an account with that email. They can create one on their phone in a minute.'
        : e instanceof Error
          ? e.message
          : undefined,
    });
  };

  const checkCode = useCallback(
    async (raw: string) => {
      if (busy.current) return;
      busy.current = true;
      try {
        show(await api<CheckinResult>(`${base}/checkins`, { method: 'POST', body: { code: raw } }));
      } catch (e) {
        fail(e, 'ticket');
      } finally {
        busy.current = false;
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [base, show],
  );

  async function submitCode(e: FormEvent) {
    e.preventDefault();
    if (!code.trim()) return;
    await checkCode(code);
    setCode('');
  }

  async function submitEmail(e: FormEvent) {
    e.preventDefault();
    try {
      show(await api<CheckinResult>(`${base}/checkins/by-email`, { method: 'POST', body: { email } }));
      setEmail('');
    } catch (err) {
      fail(err, 'account');
    }
  }

  async function undo(userId: string) {
    await api<void>(`${base}/checkins/${userId}`, { method: 'DELETE' });
    client.invalidateQueries({ queryKey: ['admin', `${base}/checkins`] });
  }

  const rows = (list.data?.attendees ?? []).filter((a) =>
    `${a.name} ${a.email} ${a.code}`.toLowerCase().includes(filter.trim().toLowerCase()),
  );

  return (
    <div className="stack-l">
      <section className="checkin-grid">
        <div className="stack">
          <h2>Scan tickets</h2>
          <Scanner onCode={checkCode} />
          <div aria-live="assertive">
            {outcome && (
              <div className={`scan-result is-${outcome.kind}`}>
                <strong>{outcome.title}</strong>
                {outcome.detail && <span>{outcome.detail}</span>}
              </div>
            )}
          </div>
        </div>
        <div className="stack">
          <form className="panel form" onSubmit={submitCode}>
            <h2>Type a code</h2>
            <p className="muted small">The 8 letters and numbers under their QR code.</p>
            <div className="row">
              <input aria-label="Ticket code" value={code} onChange={(e) => setCode(e.target.value)} placeholder="ABCD 2345" autoCapitalize="characters" autoComplete="off" />
              <button className="btn">Check in</button>
            </div>
          </form>
          <form className="panel form" onSubmit={submitEmail}>
            <h2>No phone? Use their email</h2>
            <div className="row">
              <input aria-label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@example.com" />
              <button className="btn secondary">Check in</button>
            </div>
          </form>
          {list.data && (
            <p className="checkin-count">
              <strong>{list.data.checkedIn}</strong> checked in
              <span className="muted"> of {list.data.tickets} {list.data.tickets === 1 ? "person" : "people"} who opened their ticket</span>
            </p>
          )}
        </div>
      </section>

      <section className="stack">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h2>Attendees</h2>
          <input aria-label="Search attendees" placeholder="Search name, email or code" value={filter} onChange={(e) => setFilter(e.target.value)} />
        </div>
        {list.isPending ? (
          <Loading />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => list.refetch()} />
        ) : rows.length === 0 ? (
          <Empty title={filter ? 'Nobody matches that search' : 'No tickets yet'}>
            {!filter && <p>People appear here once they open "Ticket" in the app or you check them in.</p>}
          </Empty>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Code</th>
                  <th>Checked in</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((a) => (
                  <tr key={a.userId}>
                    <td>{a.name}</td>
                    <td>{a.email}</td>
                    <td className="mono-code">{a.code}</td>
                    <td>{a.checkedInAt ? formatDateTime(a.checkedInAt, event.timezone) : <span className="muted">Not yet</span>}</td>
                    <td>
                      {a.checkedInAt ? (
                        <button className="btn secondary small" onClick={() => undo(a.userId)}>
                          Undo
                        </button>
                      ) : (
                        <button className="btn small" onClick={() => checkCode(a.code)}>
                          Check in
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

/** Camera QR scanner. Needs HTTPS (or localhost) because browsers only allow the camera there. */
function Scanner({ onCode }: { onCode: (text: string) => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const [state, setState] = useState<'off' | 'starting' | 'on' | 'error'>('off');
  const [problem, setProblem] = useState('');
  const stream = useRef<MediaStream | null>(null);
  const last = useRef<{ text: string; at: number }>({ text: '', at: 0 });
  // The QR decoder is only downloaded when someone actually starts the camera.
  const decoder = useRef<typeof import('jsqr').default | null>(null);

  const stop = useCallback(() => {
    stream.current?.getTracks().forEach((t) => t.stop());
    stream.current = null;
    setState('off');
  }, []);

  useEffect(() => stop, [stop]);

  async function start() {
    if (!navigator.mediaDevices?.getUserMedia) {
      setState('error');
      setProblem(
        window.isSecureContext
          ? "This browser can't use the camera. Type the code instead."
          : 'The camera only works over https. Open the app through its https link, or type the code instead.',
      );
      return;
    }
    setState('starting');
    try {
      decoder.current ??= (await import('jsqr')).default;
      const s = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
      stream.current = s;
      if (video.current) {
        video.current.srcObject = s;
        await video.current.play();
      }
      setState('on');
    } catch {
      setState('error');
      setProblem('No camera access. Allow the camera for this site in your browser settings, or type the code instead.');
    }
  }

  useEffect(() => {
    if (state !== 'on') return;
    let frame = 0;
    let lastScan = 0;
    const tick = (time: number) => {
      frame = requestAnimationFrame(tick);
      const v = video.current;
      const c = canvas.current;
      const jsQR = decoder.current;
      if (!v || !c || !jsQR || v.readyState < 2 || time - lastScan < 200) return;
      lastScan = time;
      const scale = Math.min(1, 480 / v.videoWidth);
      c.width = Math.round(v.videoWidth * scale);
      c.height = Math.round(v.videoHeight * scale);
      const ctx = c.getContext('2d', { willReadFrequently: true });
      if (!ctx) return;
      ctx.drawImage(v, 0, 0, c.width, c.height);
      const found = jsQR(ctx.getImageData(0, 0, c.width, c.height).data, c.width, c.height, { inversionAttempts: 'dontInvert' });
      if (found?.data) {
        const now = Date.now();
        // The same ticket stays in view for a while; only send it again after a pause.
        if (found.data !== last.current.text || now - last.current.at > 4000) {
          last.current = { text: found.data, at: now };
          onCode(found.data);
        }
      }
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [state, onCode]);

  return (
    <div className="scanner">
      <video ref={video} playsInline muted className={state === 'on' ? '' : 'is-hidden'} />
      <canvas ref={canvas} hidden />
      {state === 'on' ? (
        <button className="btn secondary" onClick={stop}>
          Stop camera
        </button>
      ) : (
        <div className="scanner-off stack">
          {state === 'error' && <p className="error">{problem}</p>}
          <button className="btn" onClick={start} disabled={state === 'starting'}>
            {state === 'starting' ? 'Starting camera…' : 'Start camera'}
          </button>
        </div>
      )}
    </div>
  );
}

// ---------------- Feedback ----------------

export function FeedbackTab({ base, event }: { base: string; event: EventView }) {
  const data = useQuery({
    queryKey: ['admin', `${base}/feedback`],
    queryFn: () => api<SessionFeedbackView[]>(`${base}/feedback`),
  });
  if (data.isPending) return <Loading />;
  if (data.isError) return <ErrorState error={data.error} onRetry={() => data.refetch()} />;
  if (data.data.length === 0) {
    return (
      <Empty title="No feedback yet">
        <p>Attendees can rate a session from its page once it has started. Ratings show up here, without names.</p>
      </Empty>
    );
  }
  return (
    <div className="stack">
      <p className="muted">Ratings and comments are anonymous. Share them with speakers as you see fit.</p>
      <ul className="feedback-list stack">
        {data.data.map((s) => (
          <li key={s.sessionId} className="panel stack">
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <div>
                <h3>{s.title}</h3>
                <p className="muted small">{formatDateTime(s.startsAt, event.timezone)}</p>
              </div>
              <p className="feedback-score">
                <strong>{s.averageRating?.toFixed(1)}</strong>
                <Stars value={s.averageRating ?? 0} />
                <span className="muted small">
                  {s.responses} {s.responses === 1 ? 'rating' : 'ratings'}
                </span>
              </p>
            </div>
            <div className="bars" aria-label="Ratings breakdown">
              {[5, 4, 3, 2, 1].map((n) => {
                const count = s.ratingCounts[n - 1] ?? 0;
                return (
                  <div key={n} className="bar-row">
                    <span>{n}★</span>
                    <span className="bar">
                      <span style={{ width: `${s.responses ? (count / s.responses) * 100 : 0}%` }} />
                    </span>
                    <span className="muted">{count}</span>
                  </div>
                );
              })}
            </div>
            {s.comments.length > 0 && (
              <details>
                <summary>
                  {s.comments.length} {s.comments.length === 1 ? 'comment' : 'comments'}
                </summary>
                <ul className="comment-list">
                  {s.comments.map((c, i) => (
                    <li key={i}>
                      <Stars value={c.rating} /> {c.comment}
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
