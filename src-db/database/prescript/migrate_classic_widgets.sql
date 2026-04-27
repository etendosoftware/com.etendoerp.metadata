-- Migration: Classic obkmo_widget_class → etmeta_widget_class
-- Migrates 9 QUERY_LIST + 1 URL widget from the classic widget system.
-- Notes:
--   @optional_filters@ macro has been stripped (not supported in the new system).
--   Run this script once, then execute ./gradlew export.database to regenerate the XML.

DO $$
DECLARE
  v_client  VARCHAR(32) := '0';
  v_org     VARCHAR(32) := '0';
  v_module  VARCHAR(32) := '51E67C9184F6439595409B46040FC572';
  v_user    VARCHAR(32) := '100';
BEGIN

  -- -------------------------------------------------------------------------
  -- QUERY_LIST widgets
  -- -------------------------------------------------------------------------

  INSERT INTO etmeta_widget_class (
    etmeta_widget_class_id, ad_client_id, ad_org_id, isactive,
    createdby, updatedby, name, type, title, description,
    hql_query, default_width, default_height, refresh_interval, ad_module_id
  ) VALUES

  ( 'A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6', v_client, v_org, 'Y', v_user, v_user,
    'best-sellers', 'QUERY_LIST', 'Best Sellers',
    'Top selling products by ordered quantity',
    'select product.id as id, product.name as pname, sum(orderedQuantity) as qty, product.uOM.name as uom, product.uOM.id as uomid
from OrderLine ol
where ol.salesOrder.documentStatus = ''CO''
  and ol.salesOrder.salesTransaction = ''Y''
  and ol.client.id = :client
  and ol.product.name like :pname
  and ol.organization.id in (:organizationList)
group by product.name, product.uOM.name, product.id, product.uOM.id
order by sum(orderedQuantity) desc',
    4, 2, 0, v_module ),

  ( 'B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7', v_client, v_org, 'Y', v_user, v_user,
    'invoices-to-collect', 'QUERY_LIST', 'Invoices to Collect',
    'Processed sales invoices pending payment collection',
    'select inv.id as invoiceId, inv.documentNo as documentNo,
  inv.businessPartner.id as businessPartnerId, inv.businessPartner.name as businessPartnerName,
  inv.invoiceDate as invoiceDate, inv.grandTotalAmount as grandTotalAmount,
  inv.currency.iSOCode as currency, inv.paymentTerms.name as paymentTerms,
  inv.outstandingAmount as outstandingAmount, inv.daysTillDue as daysTillDue,
  inv.dueAmount as dueAmount, inv.organization.name as organizationName
from Invoice as inv
where inv.businessPartner.name like :businessPartnerName
  and inv.documentNo like :documentNo
  and inv.processed = true
  and inv.documentStatus <> ''VO''
  and inv.paymentComplete = false
  and inv.salesTransaction = true
  and inv.client.id = :client
  and inv.organization.id in (:organizationList)
  and inv.organization.name like :organizationName
order by inv.invoiceDate',
    4, 2, 0, v_module ),

  ( 'C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8', v_client, v_org, 'Y', v_user, v_user,
    'invoices-to-pay', 'QUERY_LIST', 'Invoices to Pay',
    'Processed purchase invoices pending payment',
    'select inv.id as invoiceId, inv.documentNo as documentNo,
  inv.businessPartner.id as businessPartnerId, inv.businessPartner.name as businessPartnerName,
  inv.invoiceDate as invoiceDate, inv.grandTotalAmount as grandTotalAmount,
  inv.currency.iSOCode as currency, inv.paymentTerms.name as paymentTerms,
  inv.outstandingAmount as outstandingAmount, inv.daysTillDue as daysTillDue,
  inv.dueAmount as dueAmount, inv.organization.name as organizationName
from Invoice as inv
where inv.businessPartner.name like :businessPartnerName
  and inv.documentNo like :documentNo
  and inv.processed = true
  and inv.paymentComplete = false
  and inv.salesTransaction = false
  and inv.client.id = :client
  and inv.organization.id in (:organizationList)
  and inv.organization.name like :organizationName
order by inv.invoiceDate',
    4, 2, 0, v_module ),

  ( 'D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9', v_client, v_org, 'Y', v_user, v_user,
    'my-current-timesheets', 'QUERY_LIST', 'My Current Timesheets',
    'Timesheet lines for the current month of the logged-in user',
    'select tel.expenseSheet.documentNo as documentNo,
  tel.expenseSheet.id as expenseSheetId,
  tel.description as description,
  case when tel.expenseDate is not null then tel.expenseDate else tel.expenseSheet.reportDate end as expenseDate,
  tel.product.id as productId,
  tel.product.name as productName,
  tel.quantity as quantity,
  tel.uOM.name as uomName,
  p.id as projectId,
  p.name as projectName,
  tel.expenseSheet.processed as processed
from TimeAndExpenseSheetLine as tel
  left join tel.project as p,
  ADUser as u
where (month(current_date()) = month(tel.expenseDate)
    or (month(current_date()) = month(tel.expenseSheet.reportDate) and tel.expenseDate = null))
  and (year(current_date()) = year(tel.expenseDate)
    or (year(current_date()) = year(tel.expenseSheet.reportDate) and tel.expenseDate = null))
  and tel.timeSheet = ''Y''
  and tel.client.id = :client
  and tel.expenseSheet.businessPartner = u.businessPartner
  and u.id = :user
order by tel.expenseSheet.reportDate, tel.expenseSheet.documentNo',
    4, 2, 0, v_module ),

  ( 'E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0', v_client, v_org, 'Y', v_user, v_user,
    'payment-in-awaiting', 'QUERY_LIST', 'Payment In - Awaiting Execution',
    'Received payments processed but awaiting bank execution',
    'select p.id as finpaymentId, p.documentNo as documentNo,
  p.businessPartner.id as businessPartnerId, p.businessPartner.name as businessPartnerName,
  p.paymentDate as paymentDate, p.amount as amount,
  p.currency.iSOCode as currency, p.organization.name as organizationName
from FIN_Payment as p
where p.businessPartner.name like :businessPartnerName
  and p.documentNo like :documentNo
  and p.client.id = :client
  and p.processed = true
  and p.receipt = true
  and p.status = ''RPAE''
  and p.organization.id in (:organizationList)
  and p.organization.name like :organizationName
order by p.paymentDate',
    4, 2, 0, v_module ),

  ( 'F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1', v_client, v_org, 'Y', v_user, v_user,
    'payment-out-awaiting', 'QUERY_LIST', 'Payment Out - Awaiting Execution',
    'Outgoing payments processed but awaiting bank execution',
    'select p.id as finpaymentId, p.documentNo as documentNo,
  p.businessPartner.id as businessPartnerId, p.businessPartner.name as businessPartnerName,
  p.paymentDate as paymentDate, p.amount as amount,
  p.currency.iSOCode as currency, p.organization.name as organizationName
from FIN_Payment as p
where p.businessPartner.name like :businessPartnerName
  and p.documentNo like :documentNo
  and p.client.id = :client
  and p.processed = true
  and p.receipt = false
  and p.status = ''RPAE''
  and p.organization.id in (:organizationList)
  and p.organization.name like :organizationName
order by p.paymentDate',
    4, 2, 0, v_module ),

  ( 'A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2', v_client, v_org, 'Y', v_user, v_user,
    'pending-goods-receipt', 'QUERY_LIST', 'Pending Goods Receipt',
    'Purchase order lines past their scheduled delivery date with pending receipt',
    'select ol.salesOrder.id as orderId,
  ol.salesOrder.organization.name as organizationName,
  ol.salesOrder.orderDate as dateordered,
  ol.salesOrder.scheduledDeliveryDate as plannedDeliveryDate,
  ol.salesOrder.documentNo as salesorder,
  ol.salesOrder.businessPartner.name as bpartner,
  ol.product.name as productname,
  attr.description as attribute,
  ol.uOM.name as uom,
  ol.orderedQuantity as totalqty,
  (select coalesce(sum(po.quantity), 0)
     from ProcurementPOInvoiceMatch po
    where po.goodsShipmentLine is not null
      and po.salesOrderLine = ol) as qtyReceived,
  (select ol.orderedQuantity - coalesce(sum(po2.quantity), 0)
     from ProcurementPOInvoiceMatch po2
    where po2.goodsShipmentLine is not null
      and po2.salesOrderLine = ol) as qtyPending
from OrderLine as ol
  left join ol.attributeSetValue attr
where ol.salesOrder.client.id = :client
  and ol.salesOrder.organization.id in (:organizationList)
  and ol.salesOrder.documentStatus = ''CO''
  and ol.salesOrder.salesTransaction = false
  and ol.orderedQuantity <> (select coalesce(sum(po3.quantity), 0)
                               from ProcurementPOInvoiceMatch po3
                              where po3.goodsShipmentLine is not null
                                and po3.salesOrderLine = ol)
  and ol.salesOrder.scheduledDeliveryDate <= now()
  and ol.product.name like :productname
  and ol.salesOrder.businessPartner.name like :suppliername
  and ol.salesOrder.documentNo like :documentno
  and ol.salesOrder.organization.name like :organizationName
order by ol.salesOrder.scheduledDeliveryDate, ol.salesOrder.documentNo',
    4, 2, 0, v_module ),

  ( 'B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3', v_client, v_org, 'Y', v_user, v_user,
    'quotations', 'QUERY_LIST', 'Quotations',
    'Quotation status summary with approval/rejection percentages',
    'select q.documentStatus as Status,
  case when count(*) > 0
    then round((cast((count(*) * 100) as big_decimal) /
      (select count(*) from Order q2
       where q2.documentStatus in (''CA'', ''CJ'')
         and q2.organization.id in (:organizationList))))
    else round(cast(0 as big_decimal))
  end || ''%'' as Percentage,
  count(*) as Total
from Order q
where q.documentStatus in (''CA'', ''CJ'')
  and q.organization.id in (:organizationList)
group by q.documentStatus',
    2, 2, 0, v_module ),

  ( 'C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4', v_client, v_org, 'Y', v_user, v_user,
    'stock-by-warehouse', 'QUERY_LIST', 'Stock by Warehouse',
    'Current stock levels grouped by organization, warehouse and product',
    'select Organization.name as store, Warehouse.name as warehouse,
  Organization.id as orgid, Product.searchKey as identifier,
  Product.name as product, Product.id as pid,
  ProductCategory.name as category, ProductCategory.id as pcid,
  UOM.name as uom, sum(ps.quantityOnHand) as qtyonhand,
  AttributeSetInstance.description as attr, Warehouse.id as whid
from ProductStockView ps
  left outer join ps.product as Product
  left outer join ps.storageBin as Locator
  left outer join Locator.warehouse as Warehouse
  left outer join Product.productCategory as ProductCategory
  left outer join Warehouse.organization as Organization
  left outer join ps.uOM as UOM
  left outer join ps.attributeSetValue as AttributeSetInstance
where ps.client.id = :client
  and ps.organization.id in (:organizationList)
  and Product.stocked = ''Y''
group by Organization.name, Warehouse.name, Organization.id, Product.searchKey,
  Product.name, Product.id, ProductCategory.name, ProductCategory.id,
  UOM.name, AttributeSetInstance.description, Warehouse.id
order by Warehouse.name, Product.name, sum(ps.quantityOnHand) desc',
    4, 2, 0, v_module ),

  ( 'D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5', v_client, v_org, 'Y', v_user, v_user,
    'simple-stock-by-warehouse', 'QUERY_LIST', 'Simple Stock by Warehouse',
    'Simplified stock view by warehouse using the OBWCL_StockByWarehouseView',
    'select orgname as store, warehouseName as warehouse,
  organization.id as orgid, searchKey as identifier,
  productName as product, product.id as pid,
  productCategoryName as category, productCategory.id as pcid,
  uOMName as uom, quantityOnHand as qtyonhand,
  description as attr, warehouse.id as whid
from OBWCL_StockByWarehouseView sbw
where sbw.client.id = :client
  and sbw.organization.id in (:organizationList)
order by qtyonhand desc',
    4, 2, 0, v_module ),

  -- -------------------------------------------------------------------------
  -- URL widget
  -- -------------------------------------------------------------------------

  ( 'E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6', v_client, v_org, 'Y', v_user, v_user,
    'google-calendar', 'URL', 'Google Calendar',
    'Embed a Google Calendar via URL. Replace the src parameter with the desired calendar ID.',
    NULL, 2, 2, 0, v_module )
  -- Note: external_data_url is intentionally NULL so the user configures their own
  -- calendar URL at the dashboard-widget level via etmeta_dashboard_widget.
  ;

END $$;
