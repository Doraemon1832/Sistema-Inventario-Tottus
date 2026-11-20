import { expect, test, request } from '@playwright/test';

const backend = 'http://127.0.0.1:18080';
const viewports = [375, 390, 768, 1024, 1280, 1366, 1440, 1600, 1920];
const routes = ['/dashboard', '/inventario', '/ingresos', '/egresos', '/compras', '/proveedores', '/kardex'];

test('módulos principales no desbordan la página en móvil, tablet y escritorio', async ({ page }) => {
  test.setTimeout(120_000);
  const api = await request.newContext();
  const response = await api.post(`${backend}/api/auth/login`, {
    data: { usuario: process.env.TOTTUS_E2E_USERNAME, password: process.env.TOTTUS_E2E_PASSWORD }
  });
  expect(response.ok()).toBeTruthy();
  const user = await response.json();
  await page.addInitScript((value) => {
    localStorage.setItem('tottus_token', value.token);
    localStorage.setItem('tottus_user', JSON.stringify(value));
    localStorage.setItem('tottus_active_sede', String(value.idSede));
  }, user);

  for (const width of viewports) {
    await page.setViewportSize({ width, height: 900 });
    for (const route of routes) {
      await page.goto(route);
      await expect(page.locator('main, app-inventario, app-abastecimiento, app-dashboard').first()).toBeVisible();
      const documentWidths = await page.evaluate(() => ({
        // Use the viewport width; Safari's classic scrollbar reduces clientWidth by its gutter.
        viewport: window.innerWidth,
        document: document.documentElement.scrollWidth,
        offenders: Array.from(document.querySelectorAll<HTMLElement>('body *'))
          .map((element) => ({
            element: `${element.tagName.toLowerCase()}${element.className && typeof element.className === 'string' ? `.${element.className.trim().replace(/\s+/g, '.')}` : ''}`,
            right: Math.round(element.getBoundingClientRect().right),
            width: Math.round(element.getBoundingClientRect().width)
          }))
          .filter((item) => item.right > document.documentElement.clientWidth + 1)
          .slice(0, 6)
      }));
      expect(documentWidths.document, `${route} tiene desbordamiento a ${width}px: ${JSON.stringify(documentWidths.offenders)}`).toBeLessThanOrEqual(documentWidths.viewport + 1);
      if (width >= 1280 && route === '/inventario') {
        await expect(page.locator('.side-nav')).not.toHaveClass(/collapsed/);
        await page.locator('.inventario-table').waitFor({ state: 'visible' });
        const tableWidths = await page.locator('.table-responsive').evaluateAll((elements) =>
          elements.map((element) => ({
            client: (element as HTMLElement).clientWidth,
            scroll: (element as HTMLElement).scrollWidth,
            table: (element.querySelector('table') as HTMLElement | null)?.scrollWidth ?? 0
          }))
        );
        expect(tableWidths.length).toBeGreaterThan(0);
        expect(tableWidths.every((item) => item.scroll <= item.client + 2 && item.table <= item.client + 2),
          'cada tabla de escritorio debe caber sin scroll horizontal con el menú expandido').toBeTruthy();
      }

      if (route === '/kardex') {
        await expect(page.getByRole('heading', { name: 'Kardex de inventario' })).toBeVisible();
        if (width >= 1280) {
          const tableWidths = await page.locator('.table-responsive').evaluateAll(elements =>
            elements.map(element => ({ client: (element as HTMLElement).clientWidth, scroll: (element as HTMLElement).scrollWidth }))
          );
          expect(tableWidths.every(item => item.scroll <= item.client + 2), `la tabla Kardex debe caber a ${width}px`).toBeTruthy();
        }
      }
    }
  }
  await api.dispose();
});
