// Hook PreToolUse: chặn Claude đụng vào file chứa bí mật (.env, .env.local, thư mục secrets/, khóa và chứng chỉ).
// Đọc JSON sự kiện từ stdin, xét tool_input.file_path. Exit 2 = chặn (Claude Code đưa stderr cho Claude đọc),
// exit 0 = cho qua. Không dùng thư viện ngoài để chạy được ngay với node có sẵn, không cần npm install.
//
// Kiểm thử tay (Git Bash; số in ra là mã thoát, 2 = chặn, 0 = cho qua):
//   echo '{"tool_input":{"file_path":"C:\\labs\\labtrack\\.env"}}' | node .claude/hooks/protect-files.js; echo $?       # 2
//   echo '{"tool_input":{"file_path":"src/main/resources/.env.local"}}' | node .claude/hooks/protect-files.js; echo $?  # 2
//   echo '{"tool_input":{"file_path":"secrets/db-password.txt"}}' | node .claude/hooks/protect-files.js; echo $?        # 2
//   echo '{"tool_input":{"file_path":"certs/server.PEM"}}' | node .claude/hooks/protect-files.js; echo $?                # 2
//   echo '{"tool_input":{"file_path":".env.example"}}' | node .claude/hooks/protect-files.js; echo $?                    # 0
//   echo 'khong phai json' | node .claude/hooks/protect-files.js; echo $?                                                 # 0
// PowerShell: thay "; echo $?" bằng "; $LASTEXITCODE".

'use strict';

const path = require('node:path');

const ENV_FILES = new Set(['.env', '.env.local']);
const SECRET_DIR = 'secrets';
const KEY_EXTENSIONS = ['.pem', '.key', '.p12', '.jks', '.pfx'];

/**
 * Trả về lý do chặn, hoặc null nếu cho qua.
 * Chuẩn hóa ".." trước khi xét thư mục để "a/../secrets/x" không lọt và "secrets/../x" không bị chặn oan.
 * So sánh chữ thường và bỏ dấu chấm, khoảng trắng cuối mỗi đoạn vì Windows coi ".ENV", ".env." và ".env"
 * là cùng một file; không làm vậy thì đổi hoa thường là qua mặt được hook.
 */
function blockReason(filePath) {
  const segments = path.posix
    .normalize(filePath.replace(/\\/g, '/'))
    .toLowerCase()
    .split('/')
    .map((segment) => segment.replace(/[. ]+$/, ''));
  const name = segments.pop();

  if (ENV_FILES.has(name)) {
    return `${name} chứa biến môi trường thật`;
  }
  if (segments.includes(SECRET_DIR)) {
    return 'file nằm trong thư mục secrets/';
  }
  const extension = KEY_EXTENSIONS.find((ext) => name.endsWith(ext));
  if (extension) {
    return `file ${extension} là khóa hoặc chứng chỉ`;
  }
  return null;
}

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  input += chunk;
});
process.stdin.on('end', () => {
  let event;
  try {
    // PowerShell 5.1 gửi kèm BOM khi $OutputEncoding là UTF-8; để nguyên thì JSON.parse lỗi và hook cho qua.
    event = JSON.parse(input.replace(/^\uFEFF/, ''));
  } catch {
    return; // JSON lỗi: không can thiệp, exitCode mặc định 0.
  }

  const filePath = event?.tool_input?.file_path;
  if (typeof filePath !== 'string' || filePath === '') {
    return;
  }

  const reason = blockReason(filePath);
  if (reason) {
    process.stderr.write(
      `protect-files: chặn ${filePath} (${reason}). File này giữ bí mật thật, Claude không được đụng tới. ` +
        'Cần thêm biến cấu hình thì ghi tên biến kèm giá trị mẫu vào .env.example.\n'
    );
    // Đặt exitCode thay vì process.exit() để stderr kịp ghi hết trước khi tiến trình thoát.
    process.exitCode = 2;
  }
});
