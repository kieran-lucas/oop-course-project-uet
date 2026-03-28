# Hướng dẫn Setup GitHub — GitHub Desktop

## Bước 1: Người setup clone repo

1. Mở GitHub Desktop
2. File → Clone Repository
3. Chọn repo của nhóm → nhấn Clone
4. Nhớ vị trí folder (ví dụ: C:\Users\Quan\Documents\GitHub\auction-system)

## Bước 2: Copy file config vào repo

Mở folder repo vừa clone bằng File Explorer, copy 6 file vào đúng vị trí:

```
auction-system/
├── .github/
│   ├── workflows/
│   │   └── ci.yml              ← tạo 2 folder lồng nhau rồi bỏ file vào
│   └── pull_request_template.md
├── .gitignore
├── .editorconfig
├── README.md
└── SETUP_GUIDE.md
```

Lưu ý:
- Folder .github bắt đầu bằng dấu chấm. Windows có thể ẩn nó.
  Cách tạo: mở CMD trong folder repo → gõ `mkdir .github\workflows`
- File .gitignore và .editorconfig cũng bắt đầu bằng dấu chấm.
  Cách tạo: trong CMD gõ `copy nul .gitignore` rồi paste nội dung vào.
  Hoặc tạo trong Notepad → Save As → đổi "Save as type" thành "All files"
  → đặt tên `.gitignore` (có dấu chấm đầu).

## Bước 3: Commit và push

1. Quay lại GitHub Desktop → sẽ thấy danh sách file thay đổi bên trái
2. Kiểm tra tất cả file đều được tick ✅
3. Ở góc dưới trái:
   - Summary: `chore: initial project setup with CI/CD and coding convention`
   - Description: (để trống cũng được)
4. Nhấn "Commit to main"
5. Nhấn "Push origin" ở thanh trên

## Bước 4: Cấu hình Branch Protection

Mở trình duyệt → vào GitHub repo → Settings → Branches:

1. Nhấn "Add branch protection rule"
2. Branch name pattern: gõ `main`
3. Tick các ô sau:
   ✅ Require a pull request before merging
      → ✅ Require approvals → để số 1
   ✅ Require status checks to pass before merging
      → Search "test" → chọn "test" (sẽ xuất hiện sau khi CI chạy lần đầu)
      → ✅ Require branches to be up to date before merging
4. Kéo xuống nhấn "Create"

⚠️ Lưu ý: mục "Require status checks" có thể chưa tìm thấy "test"
nếu CI chưa chạy lần nào. Cứ tạo rule trước, quay lại thêm sau khi
có Gradle và CI chạy lần đầu.

## Bước 5: Mời thành viên

GitHub repo → Settings → Collaborators → Add people:
- Thêm GitHub username của 3 người còn lại
- Chọn role: Write

## Bước 6: 3 người còn lại clone repo

Mỗi người:
1. Mở GitHub Desktop
2. File → Clone Repository → chọn repo → Clone
3. Kiểm tra: mở folder, phải thấy README.md và .github/

## Bước 7: Mỗi người thử tạo branch + PR

### Tạo branch mới
1. GitHub Desktop → thanh trên: "Current Branch" → nhấn vào
2. Nhấn "New Branch"
3. Tên branch: `feat/add-member-ten-minh` (thay tên mình)
4. Nhấn "Create Branch"

### Sửa file
1. Nhấn "Open in Explorer" (hoặc "Show in Finder" trên Mac)
2. Mở README.md bằng bất kỳ editor nào
3. Thêm tên mình vào bảng "Thành viên" ở cuối file
4. Save

### Commit
1. Quay lại GitHub Desktop → thấy file thay đổi bên trái
2. Summary: `docs(readme): add member Ten-Minh`
3. Nhấn "Commit to feat/add-member-ten-minh"

### Push
1. Nhấn "Publish branch" (lần đầu) hoặc "Push origin"

### Tạo Pull Request
1. GitHub Desktop sẽ hiện nút "Create Pull Request" → nhấn vào
2. Trình duyệt mở ra → điền mô tả, tick checklist
3. Nhấn "Create pull request"

### Review và Merge
1. Một người KHÁC vào PR trên GitHub web
2. Nhấn "Files changed" → xem code → nhấn "Review changes"
3. Chọn "Approve" → nhấn "Submit review"
4. Quay lại tab "Conversation" → nhấn "Merge pull request"

### Cập nhật main trên máy
1. GitHub Desktop → "Current Branch" → chọn "main"
2. Nhấn "Fetch origin" → sau đó "Pull origin"
3. Xóa branch cũ: Branch → Delete → tick "Yes, delete on remote"

---

## Quy trình hàng ngày (sau khi setup xong)

```
1. Mở GitHub Desktop
2. Chọn branch "main" → Fetch → Pull (lấy code mới nhất)
3. Tạo branch mới: feat/ten-tinh-nang
4. Code trong IDE (IntelliJ)
5. Commit thường xuyên (mỗi khi xong 1 phần nhỏ)
6. Push branch lên GitHub
7. Tạo Pull Request
8. Chờ CI pass + 1 người approve
9. Merge
10. Quay về main → Pull
```

### Commit message format
- feat(scope): tính năng mới       → feat(auction): add place bid endpoint
- fix(scope): sửa lỗi             → fix(bid): reject bid below current price
- test(scope): thêm test          → test(concurrency): add race condition test
- docs(scope): tài liệu           → docs(readme): update setup instructions
- refactor(scope): tái cấu trúc   → refactor(dao): use JDBI instead of raw JDBC
- style(scope): format code        → style(all): apply google java format
- chore(scope): config/build       → chore(ci): add PostgreSQL service

### KHÔNG BAO GIỜ
- Push thẳng vào main (branch protection sẽ chặn)
- Commit file .env, password, hay database file
- Commit 1 lần duy nhất lúc cuối (đề bài cấm rõ)
- Commit code mình không hiểu (cả nhóm bị 0 điểm)

---

## Xử lý tình huống thường gặp

### "Merge conflict" khi tạo PR
1. GitHub Desktop → "Current Branch" → chọn "main"
2. Fetch + Pull
3. Chọn lại branch của bạn
4. Branch → "Update from main"
5. Nếu có conflict → GitHub Desktop hiện file bị conflict
6. Mở file → tìm đoạn <<<<<<< → sửa tay → save
7. Commit lại → Push

### Muốn xem code người khác đang làm
1. GitHub Desktop → "Current Branch" → chọn branch của họ
2. Xem file, nhưng KHÔNG commit vào branch này
3. Quay lại branch của mình khi xong

### Lỡ commit vào main
Branch protection sẽ chặn push. Nếu chưa bật protection:
1. GitHub Desktop hiện lỗi khi push
2. Branch → Rename → đổi thành feat/ten-gi-do
3. Push lên → tạo PR như bình thường
