import { expect, test } from '@playwright/test';

const user = { idUsuario: 99, usuario: 'ui-admin', rol: 'ADMINISTRADOR', idSede: 1, nombreSede: 'Sede Principal', idSedesAutorizadas: [1, 2], accesoGlobal: true };
const sites = [
  { idSede: 1, nombre: 'Sede Principal', direccion: '', estado: true },
  { idSede: 2, nombre: 'Sede Destino', direccion: '', estado: true }
];
const product = {
  idInventario: 1, idSede: 1, nombreSede: 'Sede Principal', idProducto: 10, codigoProducto: 'SKU-10',
  nombreProducto: 'Producto de prueba', unidadMedida: 'caja', categoria: 'Abarrotes', idCategoria: 1,
  precio: 5, stockActual: 12, stockMinimo: 2, ubicacion: 'A-1', estadoStock: 'OPTIMO', estado: true
};

async function openAsAdmin(page: import('@playwright/test').Page, includeMovement = false): Promise<string[]> {
  const movementRequests: string[] = [];
  await page.addInitScript(value => {
    localStorage.setItem('tottus_token', 'ui-only-token');
    localStorage.setItem('tottus_user', JSON.stringify(value));
    localStorage.setItem('tottus_active_sede', String(value.idSede));
  }, user);
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === '/api/inventario/movimientos') movementRequests.push(url.search);
    if (path === '/api/sedes' || path === '/api/traslados/sedes-destino') return route.fulfill({ json: sites });
    if (path === '/api/inventario') return route.fulfill({ json: [product] });
    if (path === '/api/inventario/resumen') return route.fulfill({ json: { totalProductos: 1, stockBajo: 0, sinStock: 0, totalUnidades: 12, movimientosHoy: 0 } });
    if (path === '/api/inventario/movimientos') {
      const content = includeMovement ? [{ idMovimiento: 12, idSede: 1, nombreSede: 'Sede Principal', idProducto: 10, codigoProducto: 'SKU-10', nombreProducto: 'Producto con nombre descriptivo para validar el alto de la fila', unidadMedida: 'caja', tipo: 'SALIDA', subtipo: 'VENTA', cantidad: 3, fecha: '2026-11-20T10:30:00', referencia: 'Documento de prueba con referencia extensa que debe truncarse de forma controlada sin romper el encabezado', usuario: 'operador01', stockAnterior: 15, stockPosterior: 12, idTraslado: null, codigoTraslado: null }] : [];
      return route.fulfill({ json: { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 } });
    }
    if (path === '/api/productos') return route.fulfill({ json: [{ idProducto: 10, codigo: 'SKU-10', nombre: 'Producto de prueba', unidadMedida: 'caja', estado: true }] });
    if (path === '/api/proveedores') return route.fulfill({ json: [{ idProveedor: 7, documento: '20123456789', nombre: 'Proveedor Demo', estado: true }] });
    if (path === '/api/traslados' || path === '/api/compras' || path === '/api/operaciones/ingresos' || path === '/api/operaciones/egresos') return route.fulfill({ json: [] });
    return route.fulfill({ json: {} });
  });
  return movementRequests;
}

test('el menú de operaciones, compras y traslados carga sus rutas en el frontend original', async ({ page }) => {
  await openAsAdmin(page);
  await page.goto('/kardex');
  await page.getByRole('button', { name: 'Activar tema oscuro' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('button', { name: 'Activar tema claro' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await expect(page.getByRole('link', { name: 'Compras' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Traslados' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Proveedores' })).toBeVisible();
  await page.getByRole('link', { name: 'Traslados' }).click();
  await expect(page).toHaveURL(/\/traslados$/);
  await expect(page.getByRole('heading', { name: 'Traslados entre sedes' })).toBeVisible();
  await expect(page.getByLabel('Sede de destino')).toContainText('Sede Destino');
  await page.getByRole('link', { name: 'Compras' }).click();
  await expect(page).toHaveURL(/\/compras$/);
  await expect(page.locator('app-abastecimiento')).toBeVisible();
});

test('ingresos y egresos muestran los tipos manuales aprobados y excluyen compras y traslados', async ({ page }) => {
  await openAsAdmin(page);
  await page.goto('/ingresos');
  const incomeOptions = await page.locator('select[name="income-subtype"] option').evaluateAll(options =>
    options.filter(option => !(option as HTMLOptionElement).disabled).map(option => (option as HTMLOptionElement).value));
  expect(incomeOptions).toEqual(['DEVOLUCION_CLIENTE', 'PRODUCCION', 'BONIFICACION_PROVEEDOR']);
  await page.getByLabel('Tipo de ingreso').selectOption('BONIFICACION_PROVEEDOR');
  await expect(page.getByLabel('Proveedor relacionado')).toBeVisible();
  await page.goto('/egresos');
  const expense = page.locator('select[name="expense-subtype"]');
  await expect(expense).toHaveValue('0: null');
  await expect(expense.locator('option:disabled')).toHaveText('Selecciona tipo de egreso');
  const expenseOptions = await expense.locator('option').evaluateAll(options =>
    options.filter(option => !(option as HTMLOptionElement).disabled).map(option => (option as HTMLOptionElement).value));
  expect(expenseOptions).toEqual(['VENTA', 'DEVOLUCION_PROVEEDOR', 'MERMA', 'DESMEDRO', 'DONACION', 'CONSUMO_INTERNO']);
  await expense.selectOption('CONSUMO_INTERNO');
});

test('Kardex filtra por motivo y los modulos principales caben en anchos soportados', async ({ page }) => {
  const requests = await openAsAdmin(page);
  await page.goto('/kardex');
  await page.getByLabel('Motivo').selectOption('COMPRA');
  await expect.poll(() => requests.some(search => new URLSearchParams(search).get('subtipo') === 'COMPRA')).toBeTruthy();
  for (const route of ['/inventario', '/ingresos', '/egresos', '/compras', '/proveedores', '/traslados', '/kardex']) {
    await page.goto(route);
    for (const width of [375, 390, 768, 1024, 1280, 1366, 1920]) {
      await page.setViewportSize({ width, height: 900 });
      const dimensions = await page.evaluate(() => ({
        page: document.documentElement.scrollWidth,
        viewport: window.innerWidth,
        tableOverflow: Array.from(document.querySelectorAll<HTMLElement>('.table-wrap')).some(el => el.scrollWidth > el.clientWidth + 2)
      }));
    expect(dimensions.page, `desborde de página a ${width}px`).toBeLessThanOrEqual(dimensions.viewport + 1);
      expect(dimensions.tableOverflow, `desborde de tabla en ${route} a ${width}px`).toBeFalsy();
    }
  }
});


test('Kardex mantiene columnas legibles con datos largos y los controles afectados respetan ambos temas', async ({ page }, testInfo) => {
  await openAsAdmin(page, true);
  await page.goto('/kardex');
  await expect(page.locator('.kardex-table tbody tr')).toHaveCount(1);
  await page.setViewportSize({ width: 1366, height: 900 });
  const tableGeometry = await page.locator('.kardex-table').evaluate(table => {
    const headers = Array.from(table.querySelectorAll('th'));
    const row = table.querySelector('tbody tr')!;
    return {
      widths: headers.map(header => Math.round(header.getBoundingClientRect().width)),
      headerHeight: Math.max(...headers.map(header => header.getBoundingClientRect().height)),
      rowHeight: row.getBoundingClientRect().height,
      referenceLines: getComputedStyle(table.querySelector('.reference-value')!).webkitLineClamp,
      productLines: getComputedStyle(table.querySelector('.product-name')!).webkitLineClamp
    };
  });
  expect(tableGeometry.widths).toHaveLength(7);
  expect(tableGeometry.widths.every(width => width >= 100)).toBeTruthy();
  expect(tableGeometry.headerHeight).toBeLessThan(70);
  expect(tableGeometry.rowHeight).toBeLessThan(110);
  expect(tableGeometry.referenceLines).toBe('2');
  expect(tableGeometry.productLines).toBe('2');
  await page.screenshot({ path: testInfo.outputPath('kardex-escritorio.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(250);
  const mobileGeometry = await page.evaluate(() => ({
    pageWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
    rowDisplay: getComputedStyle(document.querySelector('.kardex-table tbody tr')!).display,
    tableWidth: document.querySelector('.table-responsive')!.clientWidth,
    tableScrollWidth: document.querySelector('.table-responsive')!.scrollWidth
  }));
  expect(mobileGeometry.pageWidth).toBeLessThanOrEqual(mobileGeometry.viewportWidth + 1);
  expect(mobileGeometry.rowDisplay).toBe('grid');
  expect(mobileGeometry.tableScrollWidth).toBeLessThanOrEqual(mobileGeometry.tableWidth + 1);
  await page.screenshot({ path: testInfo.outputPath('kardex-movil.png'), fullPage: true });

  await page.goto('/egresos');
  await page.getByRole('button', { name: 'Activar tema oscuro' }).click();
  const darkSelect = await page.locator('select[name="expense-subtype"]').evaluate(el => ({
    color: getComputedStyle(el).color,
    background: getComputedStyle(el).backgroundColor
  }));
  expect(darkSelect.color).toBe('rgb(237, 241, 238)');
  expect(darkSelect.background).toBe('rgb(27, 32, 29)');
  await page.getByLabel('Tipo de egreso').selectOption('DEVOLUCION_PROVEEDOR');
  await expect(page.getByLabel('Proveedor relacionado')).toBeVisible();

  await page.goto('/traslados');
  await page.screenshot({ path: testInfo.outputPath('traslados-oscuro.png'), fullPage: true });
  const darkLine = await page.locator('.line-row').first().evaluate(el => ({
    background: getComputedStyle(el).backgroundColor,
    color: getComputedStyle(el).color,
    productLabel: getComputedStyle(el.querySelector('label')!).color
  }));
  expect(darkLine.background).toBe('rgb(45, 52, 47)');
  expect(darkLine.color).toBe('rgb(237, 241, 238)');
  expect(darkLine.productLabel).toBe('rgb(192, 203, 195)');
  const darkSite = await page.locator('.sede-selector-wrapper').evaluate(el => ({
    background: getComputedStyle(el).backgroundColor,
    text: getComputedStyle(el.querySelector('.sede-select')!).color,
    icon: getComputedStyle(el.querySelector('.sede-icon')!).color
  }));
  expect(darkSite.background).toBe('rgb(45, 52, 47)');
  expect(darkSite.text).toBe('rgb(237, 241, 238)');
  expect(darkSite.icon).toBe('rgb(128, 211, 153)');
  await page.getByRole('button', { name: 'Activar tema claro' }).click();
  const lightTheme = await page.locator('.sede-selector-wrapper').evaluate(el => ({
    background: getComputedStyle(el).backgroundColor,
    text: getComputedStyle(el.querySelector('.sede-select')!).color,
    icon: getComputedStyle(el.querySelector('.sede-icon')!).color
  }));
  expect(lightTheme.background).toBe('rgb(241, 245, 249)');
  expect(lightTheme.text).toBe('rgb(15, 23, 42)');
  expect(lightTheme.icon).toBe('rgb(27, 94, 32)');
  const lightLine = await page.locator('.line-row').first().evaluate(el => ({
    background: getComputedStyle(el).backgroundColor,
    color: getComputedStyle(el.querySelector('label')!).color
  }));
  expect(lightLine.background).toBe('rgb(245, 248, 246)');
  expect(lightLine.color).toBe('rgb(31, 41, 55)');
});
