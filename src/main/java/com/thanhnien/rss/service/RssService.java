package com.thanhnien.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.thanhnien.rss.model.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.thanhnien.rss.model.Category;
import com.thanhnien.rss.model.RssFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

@Service
public class RssService {

        @Autowired
        @Lazy
        private RssService self;

        private static final Logger logger = LoggerFactory.getLogger(RssService.class);
        private static final String BASE_RSS_URL = "https://thanhnien.vn/rss/";
        private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        // Predefined categories from thanhnien.vn
        private final List<Category> categories = Arrays.asList(
                        Category.builder()
                                        .name("Trang chủ")
                                        .slug("home")
                                        .rssUrl(BASE_RSS_URL + "home.rss")
                                        .build(),
                        Category.builder()
                                        .name("Thời sự")
                                        .slug("thoi-su")
                                        .rssUrl(BASE_RSS_URL + "thoi-su.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Pháp luật").slug("phap-luat")
                                                                        .rssUrl(BASE_RSS_URL + "thoi-su/phap-luat.rss")
                                                                        .build(),
                                                        Category.builder().name("Dân sinh").slug("dan-sinh")
                                                                        .rssUrl(BASE_RSS_URL + "thoi-su/dan-sinh.rss")
                                                                        .build(),
                                                        Category.builder().name("Lao động - Việc làm")
                                                                        .slug("lao-dong-viec-lam")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-su/lao-dong-viec-lam.rss")
                                                                        .build(),
                                                        Category.builder().name("Quyền được biết")
                                                                        .slug("quyen-duoc-biet")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-su/quyen-duoc-biet.rss")
                                                                        .build(),
                                                        Category.builder().name("Phóng sự / Điều tra")
                                                                        .slug("phong-su-dieu-tra")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-su/phong-su--dieu-tra.rss")
                                                                        .build(),
                                                        Category.builder().name("Quốc phòng").slug("quoc-phong")
                                                                        .rssUrl(BASE_RSS_URL + "thoi-su/quoc-phong.rss")
                                                                        .build(),
                                                        Category.builder().name("Chống tin giả").slug("chong-tin-gia")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-su/chong-tin-gia.rss")
                                                                        .build(),
                                                        Category.builder().name("Thành tựu y khoa")
                                                                        .slug("thanh-tuu-y-khoa")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-su/thanh-tuu-y-khoa.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Chính trị")
                                        .slug("chinh-tri")
                                        .rssUrl(BASE_RSS_URL + "chinh-tri.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Sự kiện").slug("su-kien")
                                                                        .rssUrl(BASE_RSS_URL + "chinh-tri/su-kien.rss")
                                                                        .build(),
                                                        Category.builder().name("Vươn mình trong kỷ nguyên mới")
                                                                        .slug("vuon-minh-trong-ky-nguyen-moi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "chinh-tri/vuon-minh-trong-ky-nguyen-moi.rss")
                                                                        .build(),
                                                        Category.builder().name("Thời luận").slug("thoi-luan")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "chinh-tri/thoi-luan.rss")
                                                                        .build(),
                                                        Category.builder().name("Thi đua yêu nước")
                                                                        .slug("thi-dua-yeu-nuoc")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "chinh-tri/thi-dua-yeu-nuoc.rss")
                                                                        .build(),
                                                        Category.builder().name("Chung dòng máu Lạc Hồng")
                                                                        .slug("chung-dong-mau-lac-hong")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "chinh-tri/chung-dong-mau-lac-hong.rss")
                                                                        .build(),
                                                        Category.builder().name("Góp ý văn kiện đại hội Đảng")
                                                                        .slug("gop-y-van-kien-dai-hoi-dang")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "chinh-tri/gop-y-van-kien-dai-hoi-dang.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Thế giới")
                                        .slug("the-gioi")
                                        .rssUrl(BASE_RSS_URL + "the-gioi.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Kinh tế thế giới")
                                                                        .slug("kinh-te-the-gioi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-gioi/kinh-te-the-gioi.rss")
                                                                        .build(),
                                                        Category.builder().name("Quân sự").slug("quan-su")
                                                                        .rssUrl(BASE_RSS_URL + "the-gioi/quan-su.rss")
                                                                        .build(),
                                                        Category.builder().name("Góc nhìn").slug("goc-nhin")
                                                                        .rssUrl(BASE_RSS_URL + "the-gioi/goc-nhin.rss")
                                                                        .build(),
                                                        Category.builder().name("Hồ sơ").slug("ho-so")
                                                                        .rssUrl(BASE_RSS_URL + "the-gioi/ho-so.rss")
                                                                        .build(),
                                                        Category.builder().name("Người Việt năm châu")
                                                                        .slug("nguoi-viet-nam-chau")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-gioi/nguoi-viet-nam-chau.rss")
                                                                        .build(),
                                                        Category.builder().name("Chuyện lạ").slug("chuyen-la")
                                                                        .rssUrl(BASE_RSS_URL + "the-gioi/chuyen-la.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Kinh tế")
                                        .slug("kinh-te")
                                        .rssUrl(BASE_RSS_URL + "kinh-te.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Kinh tế xanh").slug("kinh-te-xanh")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "kinh-te/kinh-te-xanh.rss")
                                                                        .build(),
                                                        Category.builder().name("Chính sách - Phát triển")
                                                                        .slug("chinh-sach-phat-trien")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "kinh-te/chinh-sach-phat-trien.rss")
                                                                        .build(),
                                                        Category.builder().name("Ngân hàng").slug("ngan-hang")
                                                                        .rssUrl(BASE_RSS_URL + "kinh-te/ngan-hang.rss")
                                                                        .build(),
                                                        Category.builder().name("Chứng khoán").slug("chung-khoan")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "kinh-te/chung-khoan.rss")
                                                                        .build(),
                                                        Category.builder().name("Doanh nghiệp").slug("doanh-nghiep")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "kinh-te/doanh-nghiep.rss")
                                                                        .build(),
                                                        Category.builder().name("Khát vọng Việt Nam").slug("doanh-nhan")
                                                                        .rssUrl(BASE_RSS_URL + "kinh-te/doanh-nhan.rss")
                                                                        .build(),
                                                        Category.builder().name("Làm giàu").slug("lam-giau")
                                                                        .rssUrl(BASE_RSS_URL + "kinh-te/lam-giau.rss")
                                                                        .build(),
                                                        Category.builder().name("Địa ốc").slug("dia-oc")
                                                                        .rssUrl(BASE_RSS_URL + "kinh-te/dia-oc.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Đời sống")
                                        .slug("doi-song")
                                        .rssUrl(BASE_RSS_URL + "doi-song.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Thanh Niên và tôi")
                                                                        .slug("thanh-nien-va-toi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "doi-song/thanh-nien-va-toi.rss")
                                                                        .build(),
                                                        Category.builder().name("Tết yêu thương").slug("tet-yeu-thuong")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "doi-song/tet-yeu-thuong.rss")
                                                                        .build(),
                                                        Category.builder().name("Người sống quanh ta")
                                                                        .slug("nguoi-song-quanh-ta")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "doi-song/nguoi-song-quanh-ta.rss")
                                                                        .build(),
                                                        Category.builder().name("Gia đình").slug("gia-dinh")
                                                                        .rssUrl(BASE_RSS_URL + "doi-song/gia-dinh.rss")
                                                                        .build(),
                                                        Category.builder().name("Ẩm thực").slug("am-thuc")
                                                                        .rssUrl(BASE_RSS_URL + "doi-song/am-thuc.rss")
                                                                        .build(),
                                                        Category.builder().name("Cộng đồng").slug("cong-dong")
                                                                        .rssUrl(BASE_RSS_URL + "doi-song/cong-dong.rss")
                                                                        .build(),
                                                        Category.builder().name("Một nửa thế giới")
                                                                        .slug("mot-nua-the-gioi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "doi-song/mot-nua-the-gioi.rss")
                                                                        .build(),
                                                        Category.builder().name("Khát vọng năm rồng")
                                                                        .slug("khat-vong-nam-rong")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "doi-song/khat-vong-nam-rong.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Sức khỏe")
                                        .slug("suc-khoe")
                                        .rssUrl(BASE_RSS_URL + "suc-khoe.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Khỏe đẹp mỗi ngày")
                                                                        .slug("khoe-dep-moi-ngay")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "suc-khoe/khoe-dep-moi-ngay.rss")
                                                                        .build(),
                                                        Category.builder().name("Làm đẹp").slug("lam-dep")
                                                                        .rssUrl(BASE_RSS_URL + "suc-khoe/lam-dep.rss")
                                                                        .build(),
                                                        Category.builder().name("Giới tính").slug("gioi-tinh")
                                                                        .rssUrl(BASE_RSS_URL + "suc-khoe/gioi-tinh.rss")
                                                                        .build(),
                                                        Category.builder().name("Y tế thông minh")
                                                                        .slug("y-te-thong-minh")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "suc-khoe/y-te-thong-minh.rss")
                                                                        .build(),
                                                        Category.builder().name("Thẩm mỹ an toàn")
                                                                        .slug("tham-my-an-toan")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "suc-khoe/tham-my-an-toan.rss")
                                                                        .build(),
                                                        Category.builder().name("Tin hay y tế").slug("tin-hay-y-te")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "suc-khoe/tin-hay-y-te.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Giới trẻ")
                                        .slug("gioi-tre")
                                        .rssUrl(BASE_RSS_URL + "gioi-tre.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Sống - Yêu - Ăn - Chơi")
                                                                        .slug("song-yeu-an-choi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "gioi-tre/song-yeu-an-choi.rss")
                                                                        .build(),
                                                        Category.builder().name("Tiếp sức gen Z mùa thi")
                                                                        .slug("tiep-suc-gen-z-mua-thi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "gioi-tre/tiep-suc-gen-z-mua-thi.rss")
                                                                        .build(),
                                                        Category.builder().name("Cơ hội nghề nghiệp")
                                                                        .slug("co-hoi-nghe-nghiep")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "gioi-tre/co-hoi-nghe-nghiep.rss")
                                                                        .build(),
                                                        Category.builder().name("Đoàn - Hội").slug("doan-hoi")
                                                                        .rssUrl(BASE_RSS_URL + "gioi-tre/doan-hoi.rss")
                                                                        .build(),
                                                        Category.builder().name("Kết nối").slug("ket-noi")
                                                                        .rssUrl(BASE_RSS_URL + "gioi-tre/ket-noi.rss")
                                                                        .build(),
                                                        Category.builder().name("Khởi nghiệp").slug("khoi-nghiep")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "gioi-tre/khoi-nghiep.rss")
                                                                        .build(),
                                                        Category.builder().name("Thế giới mạng").slug("the-gioi-mang")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "gioi-tre/the-gioi-mang.rss")
                                                                        .build(),
                                                        Category.builder().name("Gương mặt trẻ").slug("guong-mat-tre")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "gioi-tre/guong-mat-tre.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Tiêu dùng")
                                        .slug("tieu-dung-thong-minh")
                                        .rssUrl(BASE_RSS_URL + "tieu-dung-thong-minh.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Mới- Mới- Mới").slug("moi-moi-moi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "tieu-dung-thong-minh/moi-moi-moi.rss")
                                                                        .build(),
                                                        Category.builder().name("Mua một chạm").slug("mua-mot-cham")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "tieu-dung-thong-minh/mua-mot-cham.rss")
                                                                        .build(),
                                                        Category.builder().name("Ở đâu rẻ?").slug("o-dau-re")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "tieu-dung-thong-minh/o-dau-re.rss")
                                                                        .build(),
                                                        Category.builder().name("Góc người tiêu dùng")
                                                                        .slug("goc-nguoi-tieu-dung")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "tieu-dung-thong-minh/goc-nguoi-tieu-dung.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Giáo dục")
                                        .slug("giao-duc")
                                        .rssUrl(BASE_RSS_URL + "giao-duc.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Tuyển sinh").slug("tuyen-sinh")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giao-duc/tuyen-sinh.rss")
                                                                        .build(),
                                                        Category.builder().name("Chọn nghề - Chọn trường")
                                                                        .slug("chon-nghe-chon-truong")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giao-duc/chon-nghe-chon-truong.rss")
                                                                        .build(),
                                                        Category.builder().name("Du học").slug("du-hoc")
                                                                        .rssUrl(BASE_RSS_URL + "giao-duc/du-hoc.rss")
                                                                        .build(),
                                                        Category.builder().name("Nhà trường").slug("nha-truong")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giao-duc/nha-truong.rss")
                                                                        .build(),
                                                        Category.builder().name("Phụ huynh").slug("phu-huynh")
                                                                        .rssUrl(BASE_RSS_URL + "giao-duc/phu-huynh.rss")
                                                                        .build(),
                                                        Category.builder().name("Tra cứu điểm thi")
                                                                        .slug("tra-cuu-diem-thi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giao-duc/tra-cuu-diem-thi.rss")
                                                                        .build(),
                                                        Category.builder().name("Ôn thi tốt nghiệp")
                                                                        .slug("on-thi-tot-nghiep")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giao-duc/on-thi-tot-nghiep.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Du lịch")
                                        .slug("du-lich")
                                        .rssUrl(BASE_RSS_URL + "du-lich.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Tin tức - Sự kiện")
                                                                        .slug("tin-tuc-su-kien")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "du-lich/tin-tuc-su-kien.rss")
                                                                        .build(),
                                                        Category.builder().name("Chơi gì, ăn đâu, đi thế nào?")
                                                                        .slug("choi-gi-an-dau-di-the-nao")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "du-lich/choi-gi-an-dau-di-the-nao.rss")
                                                                        .build(),
                                                        Category.builder().name("Bất động sản du lịch")
                                                                        .slug("bat-dong-san-du-lich")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "du-lich/bat-dong-san-du-lich.rss")
                                                                        .build(),
                                                        Category.builder().name("Câu chuyện du lịch")
                                                                        .slug("cau-chuyen-du-lich")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "du-lich/cau-chuyen-du-lich.rss")
                                                                        .build(),
                                                        Category.builder().name("Khám phá").slug("kham-pha")
                                                                        .rssUrl(BASE_RSS_URL + "du-lich/kham-pha.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Văn hóa")
                                        .slug("van-hoa")
                                        .rssUrl(BASE_RSS_URL + "van-hoa.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Sống đẹp").slug("song-dep")
                                                                        .rssUrl(BASE_RSS_URL + "van-hoa/song-dep.rss")
                                                                        .build(),
                                                        Category.builder().name("Câu chuyện văn hóa")
                                                                        .slug("cau-chuyen-van-hoa")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "van-hoa/cau-chuyen-van-hoa.rss")
                                                                        .build(),
                                                        Category.builder().name("Khảo cứu").slug("khao-cuu")
                                                                        .rssUrl(BASE_RSS_URL + "van-hoa/khao-cuu.rss")
                                                                        .build(),
                                                        Category.builder().name("Xem - Nghe").slug("xem-nghe")
                                                                        .rssUrl(BASE_RSS_URL + "van-hoa/xem-nghe.rss")
                                                                        .build(),
                                                        Category.builder().name("Sách hay").slug("sach-hay")
                                                                        .rssUrl(BASE_RSS_URL + "van-hoa/sach-hay.rss")
                                                                        .build(),
                                                        Category.builder().name("Món ngon Hà Nội")
                                                                        .slug("mon-ngon-ha-noi")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "van-hoa/mon-ngon-ha-noi.rss")
                                                                        .build(),
                                                        Category.builder().name("Nghĩa tình miền Tây")
                                                                        .slug("nghia-tinh-mien-tay")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "van-hoa/nghia-tinh-mien-tay.rss")
                                                                        .build(),
                                                        Category.builder().name("Hào khí miền Đông")
                                                                        .slug("hao-khi-mien-dong")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "van-hoa/hao-khi-mien-dong.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Giải trí")
                                        .slug("giai-tri")
                                        .rssUrl(BASE_RSS_URL + "giai-tri.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Phim").slug("phim")
                                                                        .rssUrl(BASE_RSS_URL + "giai-tri/phim.rss")
                                                                        .build(),
                                                        Category.builder().name("Truyền hình").slug("truyen-hinh")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giai-tri/truyen-hinh.rss")
                                                                        .build(),
                                                        Category.builder().name("Đời nghệ sĩ").slug("doi-nghe-si")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "giai-tri/doi-nghe-si.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Thể thao")
                                        .slug("the-thao")
                                        .rssUrl(BASE_RSS_URL + "the-thao.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("SEA Games 33").slug("sea-games-33")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-thao/sea-games-33.rss")
                                                                        .build(),
                                                        Category.builder().name("Bóng đá Thanh Niên Sinh viên")
                                                                        .slug("bong-da-thanh-nien-sinh-vien")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-thao/bong-da-thanh-nien-sinh-vien.rss")
                                                                        .build(),
                                                        Category.builder().name("Bóng đá Việt Nam")
                                                                        .slug("bong-da-viet-nam")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-thao/bong-da-viet-nam.rss")
                                                                        .build(),
                                                        Category.builder().name("Bóng đá Quốc tế")
                                                                        .slug("bong-da-quoc-te")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-thao/bong-da-quoc-te.rss")
                                                                        .build(),
                                                        Category.builder().name("Thể thao & Cộng đồng")
                                                                        .slug("the-thao-cong-dong")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-thao/the-thao-cong-dong.rss")
                                                                        .build(),
                                                        Category.builder().name("Các môn khác").slug("cac-mon-khac")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "the-thao/cac-mon-khac.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Công nghệ")
                                        .slug("cong-nghe")
                                        .rssUrl(BASE_RSS_URL + "cong-nghe.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Tin tức công nghệ")
                                                                        .slug("tin-tuc-cong-nghe")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "cong-nghe/tin-tuc-cong-nghe.rss")
                                                                        .build(),
                                                        Category.builder().name("Blockchain").slug("blockchain")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "cong-nghe/blockchain.rss")
                                                                        .build(),
                                                        Category.builder().name("Sản phẩm").slug("san-pham")
                                                                        .rssUrl(BASE_RSS_URL + "cong-nghe/san-pham.rss")
                                                                        .build(),
                                                        Category.builder().name("Xu hướng - Chuyển đổi số")
                                                                        .slug("xu-huong-chuyen-doi-so")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "cong-nghe/xu-huong-chuyen-doi-so.rss")
                                                                        .build(),
                                                        Category.builder().name("Thủ thuật").slug("thu-thuat")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "cong-nghe/thu-thuat.rss")
                                                                        .build(),
                                                        Category.builder().name("Game").slug("game")
                                                                        .rssUrl(BASE_RSS_URL + "cong-nghe/game.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Xe")
                                        .slug("xe")
                                        .rssUrl(BASE_RSS_URL + "xe.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Thị trường").slug("thi-truong")
                                                                        .rssUrl(BASE_RSS_URL + "xe/thi-truong.rss")
                                                                        .build(),
                                                        Category.builder().name("Xe điện").slug("xe-dien")
                                                                        .rssUrl(BASE_RSS_URL + "xe/xe-dien.rss")
                                                                        .build(),
                                                        Category.builder().name("Đánh giá xe").slug("danh-gia-xe")
                                                                        .rssUrl(BASE_RSS_URL + "xe/danh-gia-xe.rss")
                                                                        .build(),
                                                        Category.builder().name("Tư vấn").slug("tu-van")
                                                                        .rssUrl(BASE_RSS_URL + "xe/tu-van.rss")
                                                                        .build(),
                                                        Category.builder().name("Video").slug("video")
                                                                        .rssUrl(BASE_RSS_URL + "xe/video.rss")
                                                                        .build(),
                                                        Category.builder().name("Xe - Giao thông").slug("xe-giao-thong")
                                                                        .rssUrl(BASE_RSS_URL + "xe/xe-giao-thong.rss")
                                                                        .build(),
                                                        Category.builder().name("Xe - Đời sống").slug("xe-doi-song")
                                                                        .rssUrl(BASE_RSS_URL + "xe/xe-doi-song.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Thời trang trẻ")
                                        .slug("thoi-trang-tre")
                                        .rssUrl(BASE_RSS_URL + "thoi-trang-tre.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Thời trang 24/7")
                                                                        .slug("thoi-trang-247")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-trang-tre/thoi-trang-247.rss")
                                                                        .build(),
                                                        Category.builder().name("Giữ dáng").slug("giu-dang")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-trang-tre/giu-dang.rss")
                                                                        .build(),
                                                        Category.builder().name("Thời trang nghề & nghiệp")
                                                                        .slug("thoi-trang-nghe-nghiep")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-trang-tre/thoi-trang-nghe-nghiep.rss")
                                                                        .build(),
                                                        Category.builder().name("Tận hưởng").slug("tan-huong")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-trang-tre/tan-huong.rss")
                                                                        .build(),
                                                        Category.builder().name("Video").slug("video-thoi-trang")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-trang-tre/video.rss")
                                                                        .build(),
                                                        Category.builder().name("Thư viện thời trang")
                                                                        .slug("thu-vien-thoi-trang")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "thoi-trang-tre/thu-vien-thoi-trang.rss")
                                                                        .build()))
                                        .build(),
                        Category.builder()
                                        .name("Video")
                                        .slug("video")
                                        .rssUrl(BASE_RSS_URL + "video.rss")
                                        .subCategories(Arrays.asList(
                                                        Category.builder().name("Thời sự").slug("thoi-su")
                                                                        .rssUrl(BASE_RSS_URL + "video/thoi-su.rss")
                                                                        .build(),
                                                        Category.builder().name("Đời sống").slug("doi-song")
                                                                        .rssUrl(BASE_RSS_URL + "video/doi-song.rss")
                                                                        .build(),
                                                        Category.builder().name("Giải trí").slug("giai-tri")
                                                                        .rssUrl(BASE_RSS_URL + "video/giai-tri.rss")
                                                                        .build(),
                                                        Category.builder().name("Giáo dục").slug("giao-duc")
                                                                        .rssUrl(BASE_RSS_URL + "video/giao-duc.rss")
                                                                        .build(),
                                                        Category.builder().name("Sức khỏe").slug("suc-khoe")
                                                                        .rssUrl(BASE_RSS_URL + "video/suc-khoe.rss")
                                                                        .build(),
                                                        Category.builder().name("Thế giới").slug("the-gioi")
                                                                        .rssUrl(BASE_RSS_URL + "video/the-gioi.rss")
                                                                        .build(),
                                                        Category.builder().name("Trải nghiệm số").slug("trai-nghiem-so")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "video/trai-nghiem-so.rss")
                                                                        .build(),
                                                        Category.builder().name("Thể thao").slug("the-thao")
                                                                        .rssUrl(BASE_RSS_URL + "video/the-thao.rss")
                                                                        .build(),
                                                        Category.builder().name("Trực tuyến").slug("truc-tuyen")
                                                                        .rssUrl(BASE_RSS_URL + "video/truc-tuyen.rss")
                                                                        .build(),
                                                        Category.builder().name("Bóng đá Thanh Niên Sinh viên")
                                                                        .slug("bong-da-thanh-nien-sinh-vien")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "video/bong-da-thanh-nien-sinh-vien.rss")
                                                                        .build(),
                                                        Category.builder().name("Bí quyết ôn thi tốt nghiệp THPT")
                                                                        .slug("bi-quyet-on-thi-tot-nghiep-thpt")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "video/bi-quyet-on-thi-tot-nghiep-thpt.rss")
                                                                        .build(),
                                                        Category.builder().name("Phim ngắn Vietnamese")
                                                                        .slug("phim-ngan-vietnamese")
                                                                        .rssUrl(BASE_RSS_URL
                                                                                        + "video/phim-ngan-vietnamese.rss")
                                                                        .build()))
                                        .build());

        /**
         * Get all available categories
         */
        public List<Category> getAllCategories() {
                return categories;
        }

        /**
         * Find category by slug
         */
        public Category findCategoryBySlug(String slug) {
                for (Category category : categories) {
                        if (category.getSlug().equals(slug)) {
                                return category;
                        }
                        if (category.getSubCategories() != null) {
                                for (Category sub : category.getSubCategories()) {
                                        if (sub.getSlug().equals(slug)) {
                                                return sub;
                                        }
                                }
                        }
                }
                return null;
        }

        /**
         * Fetch RSS feed from URL
         */
        @Cacheable(value = "rssFeed", key = "#rssUrl", unless = "#result == null || #result.articles.isEmpty()")
        public RssFeed fetchRss(String rssUrl) {
                try {
                        logger.info("Fetching RSS from: {}", rssUrl);
                        URL url = java.net.URI.create(rssUrl).toURL();
                        SyndFeedInput input = new SyndFeedInput();
                        SyndFeed syndFeed = input.build(new XmlReader(url));

                        List<Article> articles = syndFeed.getEntries().parallelStream()
                                        .map(entry -> Article.builder()
                                                        .title(entry.getTitle())
                                                        .link(entry.getLink())
                                                        .description(cleanDescription(
                                                                        entry.getDescription() != null
                                                                                        ? entry.getDescription()
                                                                                                        .getValue()
                                                                                        : ""))
                                                        .pubDate(entry.getPublishedDate() != null
                                                                        ? dateFormat.format(entry.getPublishedDate())
                                                                        : "")
                                                        .imageUrl(extractImageUrl(entry))
                                                        .videoUrl(extractVideoUrl(entry.getLink()))
                                                        .author(entry.getAuthor())
                                                        .build())
                                        .collect(Collectors.toList());

                        return RssFeed.builder()
                                        .title(syndFeed.getTitle())
                                        .description(syndFeed.getDescription())
                                        .link(syndFeed.getLink())
                                        .language(syndFeed.getLanguage())
                                        .articles(articles)
                                        .build();

                } catch (Exception e) {
                        logger.error("Error fetching RSS from {}: {}", rssUrl, e.getMessage());
                        return RssFeed.builder()
                                        .title("Error")
                                        .description("Failed to fetch RSS: " + e.getMessage())
                                        .articles(new ArrayList<>())
                                        .build();
                }
        }

        /**
         * Get home page articles
         */
        public RssFeed getHomeArticles() {
                return self.fetchRss(BASE_RSS_URL + "home.rss");
        }

        /**
         * Get articles by category slug
         */
        public RssFeed getArticlesByCategory(String slug) {
                Category category = findCategoryBySlug(slug);
                if (category != null) {
                        RssFeed feed = self.fetchRss(category.getRssUrl());
                        feed.getArticles().forEach(article -> article.setCategory(category.getName()));
                        return feed;
                }
                return RssFeed.builder()
                                .title("Not Found")
                                .description("Category not found: " + slug)
                                .articles(new ArrayList<>())
                                .build();
        }

        /**
         * Get all articles from all categories
         */
        public List<RssFeed> getAllFeeds() {
                List<RssFeed> allFeeds = new ArrayList<>();
                for (Category category : categories) {
                        RssFeed feed = self.fetchRss(category.getRssUrl());
                        feed.getArticles().forEach(article -> article.setCategory(category.getName()));
                        allFeeds.add(feed);
                }
                return allFeeds;
        }

        /**
         * Extract image URL from RSS entry
         */
        private String extractImageUrl(SyndEntry entry) {
                // Try to extract from description
                if (entry.getDescription() != null) {
                        String desc = entry.getDescription().getValue();
                        Pattern pattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']");
                        Matcher matcher = pattern.matcher(desc);
                        if (matcher.find()) {
                                return matcher.group(1);
                        }
                }

                // Try to extract from enclosures
                if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
                        return entry.getEnclosures().get(0).getUrl();
                }

                return "";
        }

        /**
         * Clean HTML from description
         */
        private String cleanDescription(String description) {
                if (description == null)
                        return "";
                // Remove HTML tags but keep text
                return description.replaceAll("<[^>]*>", "").trim();
        }

        /**
         * Extract video URL from article page
         */
        private String extractVideoUrl(String articleUrl) {
                try {
                        // Only fetch for video category or if URL contains "video"
                        if (articleUrl == null || !articleUrl.contains("video")) {
                                return null;
                        }

                        Document doc = Jsoup.connect(articleUrl)
                                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                        .timeout(5000)
                                        .get();

                        // Strategy 1: Look for video tag inside table.video video (Thanh Nien specific)
                        Element videoTag = doc.selectFirst("table.video video");
                        if (videoTag != null && videoTag.hasAttr("src")) {
                                return videoTag.attr("src");
                        }

                        // Strategy 2: Look for video tag inside td.vid video (Thanh Nien specific)
                        Element videoTag2 = doc.selectFirst("td.vid video");
                        if (videoTag2 != null && videoTag2.hasAttr("src")) {
                                return videoTag2.attr("src");
                        }

                        // Strategy 3: Look for any video tag with components
                        Element anyVideo = doc.selectFirst("video[src]");
                        if (anyVideo != null) {
                                return anyVideo.attr("src");
                        }

                        // Strategy 4: Open Graph video tag
                        Element ogVideo = doc.selectFirst("meta[property=og:video]");
                        if (ogVideo != null) {
                                return ogVideo.attr("content");
                        }

                        // Strategy 5: Look for video content div with data attributes
                        Element videoDiv = doc.selectFirst("div.cms-video-player");
                        if (videoDiv != null && videoDiv.hasAttr("data-src")) {
                                return videoDiv.attr("data-src");
                        }

                } catch (Exception e) {
                        logger.error("Error extracting video URL from {}: {}", articleUrl, e.getMessage());
                }
                return null;
        }
}
