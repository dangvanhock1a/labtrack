// Hook PreToolUse: chặn Claude sửa migration Flyway đã có trên origin/main hoặc main.
// Flyway lưu checksum của từng migration đã chạy; sửa lại file thì validate thất bại trên mọi CSDL đã áp dụng nó,
// nên đổi schema phải bằng migration mới. Exit 2 = chặn (stderr đưa cho Claude đọc), exit 0 = cho qua.
//
// Kiểm thử tay (Git Bash, ở gốc repo; số in ra là mã thoát):
//   echo '{"tool_name":"Edit","cwd":"C:/labs/labtrack","tool_input":{"file_path":"src/main/resources/db/migration/V1__create_lab_sample.sql"}}' | node .claude/hooks/protect-applied-migrations.js; echo $?  # 2
//   echo '{"tool_name":"Write","cwd":"C:/labs/labtrack","tool_input":{"file_path":"src/main/resources/db/migration/V99__thu.sql"}}' | node .claude/hooks/protect-applied-migrations.js; echo $?            # 0: file chưa có
//   echo '{"tool_name":"Read","cwd":"C:/labs/labtrack","tool_input":{"file_path":"src/main/resources/db/migration/V1__create_lab_sample.sql"}}' | node .claude/hooks/protect-applied-migrations.js; echo $?  # 0: chỉ xét Edit/Write
// PowerShell: thay "; echo $?" bằng "; $LASTEXITCODE".

'use strict';

const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const GUARDED_TOOLS = new Set(['Edit', 'Write']);
const BASE_BRANCHES = ['origin/main', 'main'];
// Phiên bản Flyway có thể có số phụ (V6_1__, V6.1__); repo đã có V6_1__ nên không chỉ bắt V<số>__.
const VERSIONED_MIGRATION = /(?:^|\/)db\/migration\/V\d+(?:[._]\d+)*__[^/]+\.sql$/i;
const MAJOR_VERSION = /^V(\d+)(?:[._]\d+)*__[^/]+\.sql$/i;

/** Chạy git không qua shell; trả stdout, hoặc null khi git lỗi (không phải repo, thiếu ref, chưa cài git). */
function git(args, cwd) {
  try {
    return execFileSync('git', args, {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
      windowsHide: true,
    });
  } catch {
    return null;
  }
}

/**
 * Số phiên bản chính kế tiếp, xét cả file trên đĩa lẫn trên nhánh gốc: nhánh hiện tại có thể tụt sau origin/main,
 * chỉ nhìn đĩa thì dễ gợi ý trùng số với migration người khác đã merge.
 */
function nextMajorVersion(dir, prefix, branch) {
  const onBranch = (git(['ls-tree', '--full-tree', '--name-only', branch, '--', prefix], dir) ?? '')
    .split('\n')
    .map((p) => path.posix.basename(p));
  const majors = [...fs.readdirSync(dir), ...onBranch]
    .map((name) => MAJOR_VERSION.exec(name))
    .filter(Boolean)
    .map((match) => Number(match[1]));
  return Math.max(0, ...majors) + 1;
}

/**
 * Trả về { relPath, branch, next } nếu file là migration đã có trên nhánh gốc, ngược lại null.
 * realpathSync.native vừa kiểm tra file tồn tại vừa trả tên thật trên đĩa: Windows có thể đưa tên ngắn
 * (C:\Users\ABC~1\...) hoặc sai hoa thường, còn tree của git phân biệt hoa thường. Phần đường dẫn trong repo
 * lấy từ `git rev-parse --show-prefix` thay vì tự cắt chuỗi, cũng vì lý do đó.
 */
function findAppliedMigration(filePath, cwd) {
  let realPath;
  try {
    realPath = fs.realpathSync.native(path.resolve(cwd, filePath));
  } catch {
    return null; // File chưa tồn tại: tạo migration mới luôn được phép.
  }
  if (!VERSIONED_MIGRATION.test(realPath.replace(/\\/g, '/'))) {
    return null;
  }

  const dir = path.dirname(realPath);
  const prefix = git(['rev-parse', '--show-prefix'], dir);
  if (prefix === null) {
    return null; // Không phải git repo.
  }
  const relDir = prefix.trim();
  const relPath = relDir + path.basename(realPath);
  const branch = BASE_BRANCHES.find((b) => git(['cat-file', '-e', `${b}:${relPath}`], dir) !== null);
  if (!branch) {
    return null;
  }
  return { relPath, relDir, branch, next: nextMajorVersion(dir, relDir, branch) };
}

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  input += chunk;
});
process.stdin.on('end', () => {
  let event;
  try {
    // Bỏ BOM như protect-files.js: PowerShell 5.1 gửi kèm khi $OutputEncoding là UTF-8.
    event = JSON.parse(input.replace(/^\uFEFF/, ''));
  } catch {
    return; // JSON lỗi: không can thiệp, exitCode mặc định 0.
  }

  const filePath = event?.tool_input?.file_path;
  if (!GUARDED_TOOLS.has(event?.tool_name) || typeof filePath !== 'string' || filePath === '') {
    return;
  }

  const cwd = typeof event.cwd === 'string' && event.cwd !== '' ? event.cwd : process.cwd();
  const applied = findAppliedMigration(filePath, cwd);
  if (applied) {
    process.stderr.write(
      `protect-applied-migrations: ${applied.relPath} đã có trên ${applied.branch}. ` +
        'KHÔNG sửa migration đã áp dụng (Flyway sẽ báo sai checksum trên mọi CSDL đã chạy nó). ' +
        `Hãy tạo V${applied.next}__<mo_ta>.sql trong ${applied.relDir}\n`
    );
    // Đặt exitCode thay vì process.exit() để stderr kịp ghi hết trước khi tiến trình thoát.
    process.exitCode = 2;
  }
});
