import type { TableProps } from 'antd';
import type { SortOrder } from 'antd/es/table/interface';
import { useState } from 'react';

/** Controlled Ant Design sorting; the API sorts before pagination. */
export function useTableSorting<T extends object>(onSort: () => void) {
  const [selection, setSelection] = useState<{ key: string; order: SortOrder }>();
  const onChange: TableProps<T>['onChange'] = (_, __, sorter, extra) => {
    if (extra.action !== 'sort') return;
    const active = Array.isArray(sorter) ? sorter[0] : sorter;
    setSelection(
      active.order && active.columnKey
        ? { key: String(active.columnKey), order: active.order }
        : undefined,
    );
    onSort();
  };

  return {
    sort: selection
      ? `${selection.key},${selection.order === 'ascend' ? 'asc' : 'desc'}`
      : undefined,
    column: (key: string) => ({
      key,
      sorter: true,
      sortOrder: selection?.key === key ? selection.order : null,
    }),
    onChange,
  };
}
