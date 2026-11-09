import type { PageResponse } from '../../../types/api';

export interface AddOnManagementItem {
  quantityUnit?: import('../../booking/types/addon-unit').AddOnQuantityUnit;
  id: string;
  code: string;
  name: string;
  description: string | null;
  price: number;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export type AddOnManagementPage = PageResponse<AddOnManagementItem>;

export interface AddOnListParams {
  page: number;
  size: number;
  search?: string;
  active?: boolean;
  sort: string;
}

export interface CreateAddOnPayload {
  quantityUnit?: import('../../booking/types/addon-unit').AddOnQuantityUnit;
  code: string;
  name: string;
  description?: string | null;
  price: number;
}

export interface UpdateAddOnPayload {
  quantityUnit?: import('../../booking/types/addon-unit').AddOnQuantityUnit;
  name: string;
  description?: string | null;
  price: number;
}

export interface AddOnFormValues {
  quantityUnit?: import('../../booking/types/addon-unit').AddOnQuantityUnit;
  code: string;
  name: string;
  description?: string;
  price: number;
}
