import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../api/http';
import { Empty, ErrorState, Field, Loading, Notice } from '../../components/ui';
import { fromLocalInput, toLocalInput } from '../../lib/time';

export type FieldType = 'text' | 'textarea' | 'url' | 'number' | 'checkbox' | 'select' | 'datetime' | 'multiselect';

export interface FieldDef {
  name: string;
  label: string;
  type: FieldType;
  required?: boolean;
  hint?: string;
  options?: { value: string; label: string }[];
}

export type FormValues = Record<string, string | number | boolean | string[] | null>;

interface CrudTabProps<T extends { id: string }> {
  /** e.g. /api/v1/admin/events/{id}/speakers */
  endpoint: string;
  /** Singular noun for buttons and headings, e.g. "speaker". */
  noun: string;
  fields: FieldDef[];
  columns: { label: string; render: (row: T) => ReactNode }[];
  /** Values for the form: defaults for a new item, or the item being edited. */
  toForm: (row?: T) => FormValues;
  /** Turns form values into the JSON body the API expects. */
  toBody?: (values: FormValues) => object;
  timezone: string;
  emptyText: string;
  /** Other queries to refresh after a change (e.g. public pages). */
  invalidate?: unknown[][];
  intro?: ReactNode;
}

export function CrudTab<T extends { id: string }>(props: CrudTabProps<T>) {
  const { endpoint, noun, fields, columns, toForm, toBody, timezone, emptyText, invalidate = [], intro } = props;
  const client = useQueryClient();
  const list = useQuery({ queryKey: ['admin', endpoint], queryFn: () => api<T[]>(endpoint) });
  const [editing, setEditing] = useState<T | 'new' | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = () => {
    client.invalidateQueries({ queryKey: ['admin', endpoint] });
    invalidate.forEach((key) => client.invalidateQueries({ queryKey: key }));
  };

  const remove = useMutation({
    mutationFn: (id: string) => api<void>(`${endpoint}/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      refresh();
      setMessage(`Deleted the ${noun}.`);
    },
    onError: (e) => setMessage(e instanceof Error ? e.message : 'Delete failed.'),
  });

  if (list.isPending) return <Loading />;
  if (list.isError) return <ErrorState error={list.error} onRetry={() => list.refetch()} />;

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <div>{intro}</div>
        <button className="btn" onClick={() => { setMessage(null); setEditing('new'); }}>
          Add {noun}
        </button>
      </div>
      {message && <Notice>{message}</Notice>}
      {list.data.length === 0 ? (
        <Empty title={`No ${noun}s yet`}>
          <p>{emptyText}</p>
        </Empty>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                {columns.map((c) => (
                  <th key={c.label} scope="col">
                    {c.label}
                  </th>
                ))}
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {list.data.map((row) => (
                <tr key={row.id}>
                  {columns.map((c) => (
                    <td key={c.label}>{c.render(row)}</td>
                  ))}
                  <td>
                    <div className="row" style={{ flexWrap: 'nowrap', gap: '0.5rem' }}>
                      <button className="btn small secondary" onClick={() => { setMessage(null); setEditing(row); }}>
                        Edit
                      </button>
                      <button
                        className="btn small danger"
                        onClick={() => {
                          if (window.confirm(`Delete this ${noun}? This can't be undone.`)) remove.mutate(row.id);
                        }}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && (
        <EditDialog
          title={editing === 'new' ? `Add ${noun}` : `Edit ${noun}`}
          fields={fields}
          initial={toForm(editing === 'new' ? undefined : editing)}
          timezone={timezone}
          onClose={() => setEditing(null)}
          onSave={async (values) => {
            const body = toBody ? toBody(values) : values;
            if (editing === 'new') await api(endpoint, { method: 'POST', body });
            else await api(`${endpoint}/${editing.id}`, { method: 'PUT', body });
            refresh();
            setMessage(editing === 'new' ? `Added the ${noun}.` : `Saved the ${noun}.`);
            setEditing(null);
          }}
        />
      )}
    </div>
  );
}

export function EditDialog({
  title,
  fields,
  initial,
  timezone,
  onClose,
  onSave,
  saveLabel = 'Save',
}: {
  title: string;
  fields: FieldDef[];
  initial: FormValues;
  timezone: string;
  onClose: () => void;
  onSave: (values: FormValues) => Promise<void>;
  saveLabel?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);

  return (
    <dialog ref={ref} onClose={onClose} aria-label={title}>
      <div className="dialog-inner">
        <h2>{title}</h2>
        <EntityForm fields={fields} initial={initial} timezone={timezone} onSave={onSave} onCancel={onClose} saveLabel={saveLabel} />
      </div>
    </dialog>
  );
}

/** A form generated from field definitions. Datetimes are edited in the event's timezone. */
export function EntityForm({
  fields,
  initial,
  timezone,
  onSave,
  onCancel,
  saveLabel = 'Save',
}: {
  fields: FieldDef[];
  initial: FormValues;
  timezone: string;
  onSave: (values: FormValues) => Promise<void>;
  onCancel?: () => void;
  saveLabel?: string;
}) {
  const [values, setValues] = useState<FormValues>(() => {
    const v = { ...initial };
    fields.filter((f) => f.type === 'datetime').forEach((f) => (v[f.name] = toLocalInput(v[f.name] as string | null, timezone)));
    return v;
  });
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (name: string, value: FormValues[string]) => setValues((v) => ({ ...v, [name]: value }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const out: FormValues = { ...values };
    fields.forEach((f) => {
      if (f.type === 'datetime') out[f.name] = fromLocalInput(String(values[f.name] ?? ''), timezone);
      if (f.type === 'number') out[f.name] = Number(values[f.name] || 0);
      if ((f.type === 'select' || f.type === 'text' || f.type === 'url' || f.type === 'textarea') && values[f.name] === '')
        out[f.name] = f.type === 'select' ? null : '';
    });
    try {
      await onSave(out);
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, null));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="form" onSubmit={submit} noValidate>
      {error && <Notice kind="error">{error.message}</Notice>}
      {fields.map((f) => {
        const err = error?.fields[f.name];
        if (f.type === 'checkbox') {
          return (
            <label key={f.name} className="check">
              <input type="checkbox" checked={!!values[f.name]} onChange={(e) => set(f.name, e.target.checked)} />
              <span>
                {f.label}
                {f.hint && <span className="muted"> {f.hint}</span>}
              </span>
            </label>
          );
        }
        if (f.type === 'multiselect') {
          const selected = new Set((values[f.name] as string[]) ?? []);
          return (
            <fieldset key={f.name} className="field" style={{ border: 'none', padding: 0, margin: 0 }}>
              <legend className="field-label">{f.label}</legend>
              {f.options?.length ? (
                <div className="multi">
                  {f.options.map((o) => (
                    <label key={o.value} className="check">
                      <input
                        type="checkbox"
                        checked={selected.has(o.value)}
                        onChange={(e) => {
                          const next = new Set(selected);
                          if (e.target.checked) next.add(o.value);
                          else next.delete(o.value);
                          set(f.name, [...next]);
                        }}
                      />
                      <span>{o.label}</span>
                    </label>
                  ))}
                </div>
              ) : (
                <span className="hint">{f.hint ?? 'Nothing to choose from yet.'}</span>
              )}
              {err && <span className="error">{err}</span>}
            </fieldset>
          );
        }
        return (
          <Field key={f.name} label={f.label + (f.required ? '' : ' (optional)')} error={err} hint={f.hint}>
            {(p) => {
              const value = values[f.name] === null || values[f.name] === undefined ? '' : String(values[f.name]);
              if (f.type === 'textarea') return <textarea {...p} value={value} onChange={(e) => set(f.name, e.target.value)} />;
              if (f.type === 'select')
                return (
                  <select {...p} value={value} onChange={(e) => set(f.name, e.target.value)}>
                    {!f.required && <option value="">None</option>}
                    {f.options?.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                );
              const inputType = f.type === 'datetime' ? 'datetime-local' : f.type === 'url' ? 'url' : f.type === 'number' ? 'number' : 'text';
              return <input {...p} type={inputType} value={value} onChange={(e) => set(f.name, e.target.value)} />;
            }}
          </Field>
        );
      })}
      <div className="dialog-actions">
        {onCancel && (
          <button type="button" className="btn secondary" onClick={onCancel}>
            Cancel
          </button>
        )}
        <button className="btn" disabled={busy}>
          {busy ? 'Saving…' : saveLabel}
        </button>
      </div>
    </form>
  );
}
