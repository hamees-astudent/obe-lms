import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { DoorOpen, Edit2, FlaskConical, Plus, Trash2 } from 'lucide-react';
import api from '@/lib/api';
import { toast } from '@/components/ui/Toast';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Card from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Modal from '@/components/ui/Modal';
import QueryError from '@/components/ui/QueryError';
import Spinner from '@/components/ui/Spinner';
import type { RoomRequest, RoomResponse, UUID } from '@/types/api';

const roomSchema = z.object({
  name:     z.string().trim().min(1, 'Name is required').max(60),
  building: z.string().max(80).optional(),
  capacity: z.coerce.number().int('Whole seats only').min(1, 'At least 1 seat').max(2000),
  kind:     z.enum(['LECTURE', 'LAB']),
  active:   z.boolean(),
});

type RoomForm = z.infer<typeof roomSchema>;

export function useRooms() {
  return useQuery<RoomResponse[]>({
    queryKey: ['admin', 'rooms'],
    meta: { errorShownInline: true },
    queryFn: () => api.get('/admin/rooms').then((r) => r.data),
  });
}

function RoomFormModal({
  open,
  room,
  onClose,
}: {
  open: boolean;
  /** Null to create. */
  room: RoomResponse | null;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const form = useForm<RoomForm>({
    resolver: zodResolver(roomSchema),
    values: room
      ? { name: room.name, building: room.building ?? '', capacity: room.capacity, kind: room.kind, active: room.active }
      : { name: '', building: '', capacity: 40, kind: 'LECTURE', active: true },
  });

  const saveMut = useMutation({
    mutationFn: (body: RoomRequest) =>
      room ? api.put(`/admin/rooms/${room.id}`, body) : api.post('/admin/rooms', body),
    onSuccess: () => {
      toast.success(room ? 'Room updated.' : 'Room added.');
      qc.invalidateQueries({ queryKey: ['admin', 'rooms'] });
      qc.invalidateQueries({ queryKey: ['admin', 'timetable'] });
      onClose();
    },
  });

  const errors = form.formState.errors;
  return (
    <Modal open={open} onClose={onClose} title={room ? `Edit ${room.name}` : 'Add Room'}>
      <form onSubmit={form.handleSubmit((d) => saveMut.mutate(d))} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Name" id="room-name" placeholder="A-101" {...form.register('name')} error={errors.name?.message} />
          <Input label="Building (optional)" id="room-building" placeholder="Academic Block" {...form.register('building')} />
          <Input label="Seats" id="room-capacity" type="number" min={1} {...form.register('capacity')} error={errors.capacity?.message} />
          <div className="flex flex-col gap-1">
            <label htmlFor="room-kind" className="text-sm font-medium text-gray-700">Type</label>
            <select
              id="room-kind"
              {...form.register('kind')}
              className="h-9 rounded-lg border border-gray-300 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="LECTURE">Lecture room</option>
              <option value="LAB">Lab</option>
            </select>
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" {...form.register('active')} className="h-4 w-4 rounded border-gray-300" />
          Available for new classes
        </label>
        <p className="text-xs text-gray-500">
          Lectures go in lecture rooms and labs in labs. A class is only placed in a room with a seat for every
          enrolled student.
        </p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={saveMut.isPending}>{room ? 'Save' : 'Add Room'}</Button>
        </div>
      </form>
    </Modal>
  );
}

/** Admin: the rooms the timetable can use. */
export default function RoomsPanel() {
  const qc = useQueryClient();
  const { data: rooms = [], isLoading, isError, error, refetch } = useRooms();
  const [editing, setEditing] = useState<RoomResponse | null>(null);
  const [formOpen, setFormOpen] = useState(false);

  const deleteMut = useMutation({
    mutationFn: (id: UUID) => api.delete(`/admin/rooms/${id}`),
    onSuccess: () => {
      toast.success('Room deleted.');
      qc.invalidateQueries({ queryKey: ['admin', 'rooms'] });
    },
  });

  function open(room: RoomResponse | null) {
    setEditing(room);
    setFormOpen(true);
  }

  const lectureSeats = rooms.filter((r) => r.active && r.kind === 'LECTURE').length;
  const labs = rooms.filter((r) => r.active && r.kind === 'LAB').length;

  return (
    <Card padding={false}>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-5 py-4">
        <div>
          <h3 className="text-base font-semibold text-gray-900">Rooms</h3>
          <p className="text-xs text-gray-500">
            {lectureSeats} lecture room{lectureSeats === 1 ? '' : 's'} and {labs} lab{labs === 1 ? '' : 's'} available
          </p>
        </div>
        <Button size="sm" onClick={() => open(null)}>
          <Plus size={14} className="mr-1" /> Add Room
        </Button>
      </div>

      {isError ? (
        <div className="p-5"><QueryError error={error} onRetry={() => refetch()} /></div>
      ) : isLoading ? (
        <div className="flex justify-center py-10"><Spinner /></div>
      ) : rooms.length === 0 ? (
        <div className="flex flex-col items-center py-10 text-center text-gray-400">
          <DoorOpen size={36} className="mb-2 opacity-40" />
          <p className="font-medium">No rooms yet</p>
          <p className="text-sm">Add lecture rooms and labs before generating the timetable.</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase tracking-wide text-gray-500">
              <tr>
                <th className="px-5 py-2 font-medium">Room</th>
                <th className="px-3 py-2 font-medium">Type</th>
                <th className="px-3 py-2 text-right font-medium">Seats</th>
                <th className="px-3 py-2 text-right font-medium">Classes / week</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-5 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rooms.map((r) => (
                <tr key={r.id} className={r.active ? '' : 'text-gray-400'}>
                  <td className="px-5 py-2.5">
                    <p className="font-medium text-gray-900">{r.name}</p>
                    {r.building && <p className="text-xs text-gray-500">{r.building}</p>}
                  </td>
                  <td className="px-3 py-2.5">
                    {r.kind === 'LAB' ? (
                      <Badge variant="purple"><FlaskConical size={11} className="mr-1" /> Lab</Badge>
                    ) : (
                      <Badge>Lecture room</Badge>
                    )}
                  </td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{r.capacity}</td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{r.weeklyClasses}</td>
                  <td className="px-3 py-2.5">
                    {r.active ? <Badge variant="success">Active</Badge> : <Badge variant="warning">Inactive</Badge>}
                  </td>
                  <td className="px-5 py-2.5">
                    <div className="flex justify-end gap-1">
                      <Button size="sm" variant="ghost" aria-label={`Edit ${r.name}`} onClick={() => open(r)}>
                        <Edit2 size={14} />
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        aria-label={`Delete ${r.name}`}
                        disabled={deleteMut.isPending}
                        onClick={() => {
                          if (confirm(`Delete room "${r.name}"?`)) deleteMut.mutate(r.id);
                        }}
                      >
                        <Trash2 size={14} className="text-red-500" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <RoomFormModal open={formOpen} room={editing} onClose={() => setFormOpen(false)} />
    </Card>
  );
}
