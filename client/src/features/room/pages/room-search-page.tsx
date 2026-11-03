import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Form } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { MainFooter } from '@components/layout/MainFooter';
import { MainHeader } from '@components/layout/MainHeader';
import { searchAvailableRoomTypes } from '../api/available-room-search-api';
import type { AvailableRoomTypeSearchParams } from '../types/available-room-search';
import { AvailableRoomResults } from './available-room-results';
import { RoomAvailabilityForm } from './room-availability-form';
import { RoomSearchHero } from './room-search-hero';
import { getInitialSearchValues, type SearchFormValues } from './room-search-utils';
import './room-search-page.css';
import { countStayNights } from '../../../utils/hotel-time';

const PAGE_SIZE = 6;

interface AvailableRoomSearchVariables {
  values: SearchFormValues;
  page: number;
}

function getSearchParams({
  values,
  page,
}: AvailableRoomSearchVariables): AvailableRoomTypeSearchParams {
  const [checkIn, checkOut] = values.stayDates;
  return {
    checkIn: checkIn.format('YYYY-MM-DD'),
    checkOut: checkOut.format('YYYY-MM-DD'),
    adults: values.adults,
    children: values.children,
    page,
    size: PAGE_SIZE,
  };
}

export function RoomSearchPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm<SearchFormValues>();
  const [submittedSearch, setSubmittedSearch] = useState<AvailableRoomSearchVariables | null>(null);

  const initialValues = useMemo(() => getInitialSearchValues(searchParams), [searchParams]);
  const watchedValues = Form.useWatch([], form) as SearchFormValues | undefined;
  const currentValues = watchedValues ?? initialValues;
  const resultValues = submittedSearch?.values ?? currentValues;
  const [checkIn, checkOut] = resultValues.stayDates;
  const params = submittedSearch ? getSearchParams(submittedSearch) : null;

  const roomsQuery = useQuery({
    queryKey: ['available-room-types', params],
    queryFn: () => searchAvailableRoomTypes(params!),
    enabled: params !== null,
    staleTime: 0,
    refetchInterval: 30_000,
    refetchOnWindowFocus: 'always',
    refetchOnReconnect: 'always',
  });
  const nights = roomsQuery.data?.content[0]?.totalNights ?? countStayNights(checkIn, checkOut);

  const handleSearch = async (values: SearchFormValues, page = 0) => {
    const search = { values, page };
    const searchParams = getSearchParams(search);
    setSubmittedSearch(search);

    try {
      const rooms = await queryClient.fetchQuery({
        queryKey: ['available-room-types', searchParams],
        queryFn: () => searchAvailableRoomTypes(searchParams),
        staleTime: 0,
      });
      if (page !== 0) return;
      if (rooms.totalElements) {
        void message.success('Đã cập nhật danh sách hạng phòng còn trống.');
      } else {
        void message.info('Không tìm thấy hạng phòng phù hợp với tiêu chí đã chọn.');
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Không thể tìm phòng trống.';
      void message.error(errorMessage);
    }
  };

  const handleBookRoom = (roomTypeId: string) => {
    const values = submittedSearch?.values ?? form.getFieldsValue();
    const [bookingCheckIn, bookingCheckOut] = values.stayDates;
    const params = new URLSearchParams({
      roomTypeId,
      checkIn: bookingCheckIn.format('YYYY-MM-DD'),
      checkOut: bookingCheckOut.format('YYYY-MM-DD'),
      adults: String(values.adults),
      children: String(values.children),
    });

    navigate(`/bookings/new?${params.toString()}`);
  };

  return (
    <div className="room-search-page">
      <MainHeader />

      <main>
        <RoomSearchHero />
        <RoomAvailabilityForm
          form={form}
          initialValues={initialValues}
          searchLoading={roomsQuery.isFetching}
          onSearch={(values) => void handleSearch(values)}
        />

        <AvailableRoomResults
          availableRooms={roomsQuery.data ?? null}
          currentValues={resultValues}
          loading={submittedSearch !== null && roomsQuery.isPending}
          nights={nights}
          onBookRoom={handleBookRoom}
          onPageChange={(page) => {
            const values = submittedSearch?.values ?? form.getFieldsValue();
            void handleSearch(values, page - 1);
          }}
        />
      </main>
      <MainFooter />
    </div>
  );
}
