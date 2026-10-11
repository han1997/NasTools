// Optional: PLAYWRIGHT_MODULE points to an installed Playwright module.
// Run with node smoke-test.cjs; does not install dependencies into the Android app.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
let browser;
const { pathToFileURL } = require('node:url');
const path = require('node:path');
const assert = require('node:assert/strict');
(async () => {
 browser=await chromium.launch({channel:'chrome',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);
 await page.screenshot({path:path.join(__dirname,'preview-desktop.png'),fullPage:true});
 const action=async a=>page.locator(`[data-action="${a}"]`).first().click();
 const go=async p=>page.locator(`#bottomNav [data-page="${p}"]`).click();
 const headerText=()=>page.locator('#appHeader').innerText();

 // 1. 文件入口：未选连接 → 选择页（不默选）
 await go('browser');
 assert.match(await headerText(),/选择连接/);
 assert.equal(await page.locator('[data-action=pick-connection]').count(),2,'both connections listed');
 await action('pick-connection');
 assert.match(await headerText(),/家庭 NAS/);

 // 2. 范围 A 边界：无搜索/筛选/缩略图
 assert.equal(await page.locator('#fileSearch').count(),0,'search removed in scope A');
 assert.equal(await page.locator('.file-list .thumb').count(),0,'no remote thumbnails');
 assert.equal(await page.locator('.chips').count(),0,'no type filter chips');

 // 3. 多层目录 + 面包屑返回
 await page.locator('[data-action="folder"][data-folder="照片"]').click();
 await page.locator('[data-action="folder"][data-folder="照片/2026 夏日山野"]').click();
 assert.equal(await page.locator('.crumbs button').count(),3,'root + 2 segments');
 await page.locator('.crumbs button[data-index="1"]').click();
 assert.equal(await page.locator('.crumbs button').count(),2,'back to 照片');

 // 4. 刷新失败保留旧列表 vs 首次加载失败
 await action('browser-menu');await action('simulate-refresh-error');
 assert(await page.getByText('刷新失败，已保留当前列表').isVisible());
 assert.ok(await page.locator('.file-row').count()>0,'old list retained');
 await action('browser-menu');await action('simulate-first-error');
 assert(await page.locator('.inline-message.error').isVisible());
 await action('retry-load');
 assert.ok(await page.locator('.file-row').count()>0,'recovered after retry');

 // 5. 媒体预览 + 失败重试
 await action('open-file');
 assert(await page.locator('.media-frame img').count());
 await action('simulate-media-error');
 assert(await page.getByText('无法加载媒体').isVisible());
 await action('retry-media');
 assert(await page.locator('.media-frame img').count());
 await action('back');
 assert.match(await headerText(),/家庭 NAS/);

 // 6. 上传：文件夹来源、冲突策略、移动上传、保存为预设
 await action('upload');
 assert.equal(await page.locator('#uploadForm button[type=submit]').isDisabled(),true,'submit disabled before source');
 await page.locator('[data-action="pick-source"][data-kind="folder"]').click();
 assert(await page.locator('#uploadForm select[name=folderPolicy]').count(),'folder conflict visible');
 await page.locator('#uploadForm select[name=policy]').selectOption('skip_existing');
 await page.locator('#uploadForm input[name=moveAfter]').check();
 await page.locator('#uploadForm input[name=savePreset]').check();
 await page.locator('#uploadForm input[name=presetName]').fill('相机增量备份');
 await page.locator('#uploadForm button[type=submit]').click();
 assert.match(await headerText(),/任务中心/);
 assert(await page.getByText('文件夹上传 · 相机 · 12 个文件 / 186 MB').first().isVisible());
 assert(await page.getByText('等待中',{exact:true}).first().isVisible());

 // 7. 任务：未知总量、加载失败重试、批量删除
 assert(await page.getByText('总计大小未知').first().isVisible());
 await action('simulate-tasks-error');
 assert(await page.locator('.inline-message.error').isVisible());
 await action('retry-tasks');
 await action('enter-selection');
 assert.match(await headerText(),/已选 0 项/);
 const cards=page.locator('[data-action="toggle-select"]');
 await cards.nth(0).click();await cards.nth(1).click();
 assert.match(await headerText(),/已选 2 项/);
 await action('batch-delete');
 assert(await page.getByText('只删除记录，不会删除 NAS 上的文件').isVisible());
 await action('confirm-batch-delete');
 assert.match(await headerText(),/任务中心/,'selection exited');

 // 8. 任务详情：警告与策略（已完成任务带跳过警告）
 await page.locator('[data-action="task-tab"][data-tab="已完成"]').click();
 await page.locator('[data-action="detail"]').first().click();
 assert(await page.locator('.warning-line').first().isVisible());
 assert(await page.getByText('续传，完整则跳过').isVisible());
 await action('back');
 await page.locator('[data-action="task-tab"][data-tab="活跃"]').click();

 // 9. 暂停/恢复
 await page.locator('[data-action="pause"]').first().click();
 assert(await page.getByText('已暂停',{exact:true}).first().isVisible());
 await page.locator('[data-action="resume"]').first().click();

 // 10. 连接编辑：未保存保护 + 保存
 await go('home');
 await action('connection-menu');
 await action('edit-connection');
 await page.locator('#connectionForm input[name=name]').fill('家庭 NAS 改');
 await action('back');
 assert(await page.getByText('未保存的修改将会丢失').isVisible());
 await action('close');
 await action('back');await action('leave');
 assert.match(await headerText(),/NasTools/);
 await action('new-connection');
 await page.locator('#connectionForm input[name=name]').fill('测试连接');
 await page.locator('#connectionForm input[name=url]').fill('https://demo.example/dav');
 await action('test-connection');
 assert(await page.getByText('模拟测试通过。未向此地址发起请求。').isVisible());
 await page.locator('#connectionForm button[type=submit]').click();
 assert(await page.getByRole('heading',{name:'测试连接'}).count());

 // 11. 删除连接：级联预设文案 + 当前连接回退
 await page.locator('[data-action="connection-menu"]').first().click();
 await action('delete-connection');
 assert(await page.getByText('将同时删除此连接相关的上传预设').isVisible());
 await action('close');
 // 删除当前浏览中的连接（家庭 NAS 是列表第一个卡片）
 await page.locator('[data-action="connection-menu"]').first().click();
 await action('delete-connection');await action('confirm-delete-connection');
 await go('browser');
 assert.match(await headerText(),/选择连接/,'deleted current connection returns to select');

 // 12. 预设：来源类型/本地选择/返回来源
 await go('home');
 await page.locator('#content [data-page="presets"]').click();
 assert.match(await headerText(),/上传预设/);
 await action('new-preset');
 await page.locator('[data-action="preset-source"][data-kind="file"]').click();
 await action('pick-local');
 await page.locator('#presetForm input[name=name]').fill('新预设');
 await page.locator('#presetForm select[name=policy]').selectOption('rename');
 await page.locator('#presetForm button[type=submit]').click();
 assert(await page.getByRole('heading',{name:'新预设'}).count());
 await action('back');
 assert.match(await headerText(),/NasTools/,'presets back to home source');

 // 13. 设置：无手动主题入口（范围 A）
 await go('settings');
 assert.equal(await page.getByText('界面主题').count(),0,'manual theme not in scope A');

 // 14. 主题切换（设计工具）+ 移动端适配
 await page.locator('#themeToggle').click();
 await page.screenshot({path:path.join(__dirname,'preview-dark.png'),fullPage:true});
 await page.setViewportSize({width:390,height:844});
 await go('browser');await page.screenshot({path:path.join(__dirname,'preview-mobile.png'),fullPage:true});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'390px no overflow');
 await page.setViewportSize({width:320,height:700});await go('home');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'320px no overflow');

 const broken=await page.locator('img').evaluateAll(imgs=>imgs.filter(i=>!i.complete||i.naturalWidth===0).map(i=>i.src));
 assert.deepEqual(broken,[]);assert.deepEqual(errors,[]);
 console.log('PASS: connection select, scope-A boundaries, nested crumbs, refresh-keep-list, media retry, full upload options, batch delete, warnings, unknown progress, unsaved guard, cascade delete, presets, settings, themes, mobile.');
 await browser.close();
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>browser?.close());
