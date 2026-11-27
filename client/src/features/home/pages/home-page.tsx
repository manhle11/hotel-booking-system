import { Button, DatePicker, Form, Select, theme, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  ArrowRightOutlined,
  CoffeeOutlined,
  FireOutlined,
  RestOutlined,
  ShopOutlined,
  SunOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

import { MainFooter } from '../../../components/layout/MainFooter';
import { MainHeader } from '../../../components/layout/MainHeader';
import { RoomTypeCatalog } from '../../room';

import './home-page.css';

const { Paragraph, Text, Title } = Typography;

const amenities = [
  {
    icon: SunOutlined,
    name: 'Hồ bơi vô cực',
    description: 'Ngắm biển Mỹ Khê bất tận',
    photo: 'photo-1540541338287-41700207dee6',
  },
  {
    icon: ShopOutlined,
    name: 'Nhà hàng Hanami',
    description: 'Ẩm thực Việt Nam và quốc tế',
    photo: 'photo-1414235077428-338989a2e8c0',
  },
  {
    icon: CoffeeOutlined,
    name: 'Quán cà phê Momo',
    description: 'Thưởng thức cà phê bên bờ biển',
    photo: 'photo-1501339847302-ac426a4a7cbb',
  },
  {
    icon: FireOutlined,
    name: 'Sky Bar & Tapas',
    description: 'Món ăn nhẹ và tầm nhìn trên cao',
    photo: 'photo-1767878032664-cd1d61f0e076',
  },
  {
    icon: RestOutlined,
    name: 'Anona Spa',
    description: 'Thư giãn cơ thể và tâm trí',
    photo: 'photo-1540555700478-4be289fbecef',
  },
  {
    icon: ThunderboltOutlined,
    name: 'Phòng gym hiện đại',
    description: 'Không gian tập luyện cân bằng',
    photo: 'photo-1534438327276-14e5300c3a48',
  },
];

interface SearchFormValues {
  checkIn: Dayjs;
  checkOut: Dayjs;
  adults: number;
  children: number;
}

export function HomePage() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const [searchForm] = Form.useForm<SearchFormValues>();
  const today = dayjs().startOf('day');
  const defaultCheckIn = today;
  const selectedCheckIn = Form.useWatch('checkIn', searchForm) ?? defaultCheckIn;

  const searchAvailableRooms = (values: SearchFormValues) => {
    const params = new URLSearchParams({
      checkIn: values.checkIn.format('YYYY-MM-DD'),
      checkOut: values.checkOut.format('YYYY-MM-DD'),
      adults: String(values.adults),
      children: String(values.children),
    });
    navigate(`/rooms/search?${params.toString()}`);
  };

  const numberOptions = (start: number, end: number, suffix: string) =>
    Array.from({ length: end - start + 1 }, (_, index) => {
      const value = start + index;
      return { value, label: `${value} ${suffix}` };
    });

  return (
    <div className="hotel-home" style={{ background: token.colorBgBase }}>
      <MainHeader />
      <main>
        <section className="home-hero" aria-label="HANBAY Hotel bên bờ biển Đà Nẵng">
          <div className="home-hero__content">
            <Text className="home-kicker" style={{ color: token.colorWhite }}>
              HANBAY HOTEL · ĐÀ NẴNG
            </Text>
            <Title
              style={{ maxWidth: 720, margin: `${token.marginSM}px 0`, color: token.colorWhite }}
            >
              Nơi biển trời chạm đến sự bình yên
            </Title>
            <Text style={{ color: token.colorWhite, fontSize: token.fontSizeLG }}>
              Trải nghiệm kỳ nghỉ tinh tế bên bờ biển Mỹ Khê.
            </Text>
          </div>
        </section>

        <section
          id="booking"
          className="booking-search-card"
          aria-label="Kiểm tra phòng trống"
          style={{
            border: `${token.lineWidth}px solid ${token.colorBorderSecondary}`,
            background: token.colorBgContainer,
            boxShadow: token.boxShadowSecondary,
          }}
        >
          <Form<SearchFormValues>
            form={searchForm}
            className="booking-search-form"
            layout="vertical"
            requiredMark={false}
            initialValues={{
              checkIn: defaultCheckIn,
              checkOut: defaultCheckIn.add(1, 'day'),
              adults: 2,
              children: 0,
            }}
            onFinish={searchAvailableRooms}
          >
            <Form.Item label="Nhận phòng" name="checkIn" rules={[{ required: true }]}>
              <DatePicker
                className="booking-search-control"
                format="DD/MM/YYYY"
                disabledDate={(date) => date.startOf('day').isBefore(today)}
                onChange={() => {
                  const checkOut = searchForm.getFieldValue('checkOut');
                  const checkIn = searchForm.getFieldValue('checkIn');
                  if (checkIn && checkOut && !checkOut.isAfter(checkIn)) {
                    searchForm.setFieldValue('checkOut', checkIn.add(1, 'day'));
                  }
                }}
              />
            </Form.Item>
            <Form.Item label="Trả phòng" name="checkOut" rules={[{ required: true }]}>
              <DatePicker
                className="booking-search-control"
                format="DD/MM/YYYY"
                disabledDate={(date) =>
                  !date.startOf('day').isAfter(selectedCheckIn.startOf('day'))
                }
              />
            </Form.Item>
            <Form.Item label="Người lớn" name="adults">
              <Select options={numberOptions(1, 12, 'người lớn')} />
            </Form.Item>
            <Form.Item label="Trẻ em" name="children">
              <Select options={numberOptions(0, 8, 'trẻ em')} />
            </Form.Item>
            <Button className="booking-search-submit" type="primary" htmlType="submit">
              Kiểm tra phòng
            </Button>
          </Form>
        </section>

        <section className="home-intro home-container" aria-labelledby="home-welcome-title">
          <div className="home-intro__copy">
            <header className="home-intro__heading">
              <span className="home-kicker">MỸ KHÊ · ĐÀ NẴNG</span>
              <h2 id="home-welcome-title">
                Chào mừng bạn đến với <span className="home-intro__brand">HANBAY</span>
              </h2>
            </header>
            <Paragraph>
              Tọa lạc bên bãi biển Mỹ Khê, HANBAY mang đến không gian nghỉ dưỡng nhẹ nhàng giữa nhịp
              sống Đà Nẵng. Mỗi căn phòng được chăm chút để đón ánh sáng và gió biển, tạo cảm giác
              thân thuộc suốt kỳ nghỉ.
            </Paragraph>
            <Button
              type="primary"
              size="large"
              href="#rooms"
              icon={<ArrowRightOutlined />}
              iconPlacement="end"
            >
              Khám phá hạng phòng
            </Button>
          </div>
          <div className="home-intro__photo">
            <img
              src="https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=1100&q=85"
              alt="Ảnh minh họa phòng nghỉ với nội thất tông gỗ và ánh sáng ấm"
              loading="lazy"
              decoding="async"
            />
          </div>
        </section>

        <RoomTypeCatalog />

        <section id="amenities" className="home-amenities" aria-labelledby="home-amenities-title">
          <div className="home-container">
            <header className="home-amenities__heading">
              <h2 id="home-amenities-title">Tiện nghi khách sạn</h2>
            </header>
            <div className="amenities-grid">
              {amenities.map(({ icon: Icon, name, description, photo }) => (
                <article className="amenity-item" key={name}>
                  <img
                    className="amenity-item__photo"
                    src={`https://images.unsplash.com/${photo}?auto=format&fit=crop&w=760&q=82`}
                    alt=""
                    loading="lazy"
                    decoding="async"
                  />
                  <span className="amenity-item__icon" aria-hidden="true">
                    <Icon />
                  </span>
                  <div>
                    <h3>{name}</h3>
                    <p>{description}</p>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="experience-section" aria-label="Ẩm thực và thư giãn">
          <div className="home-container experience-list">
            <ExperienceCard
              kicker="ẨM THỰC"
              title="Hương vị làm nên những cuộc gặp gỡ"
              description="Bắt đầu ngày mới bằng bữa sáng đầy năng lượng và khép lại buổi tối với thực đơn được tạo nên từ nguyên liệu địa phương tươi ngon."
              image="https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=1400&q=85"
              imageAlt="Không gian nhà hàng HANBAY"
              action="Khám phá nhà hàng"
            />
            <ExperienceCard
              reverse
              kicker="HÀNH TRÌNH SỨC KHỎE"
              title="Chạm vào sự tĩnh tại"
              description="Những liệu trình spa dịu nhẹ, không gian ấm áp và mùi hương thư thái giúp bạn tái tạo năng lượng sau một ngày khám phá thành phố biển."
              image="https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1400&q=85"
              imageAlt="Trải nghiệm spa tại HANBAY"
              action="Đặt lịch thư giãn"
            />
          </div>
        </section>

        <section className="home-cta">
          <div className="home-cta__content">
            <Text className="home-kicker" style={{ color: token.colorWhite }}>
              KỲ NGHỈ CỦA BẠN ĐANG CHỜ ĐỢI
            </Text>
            <Title level={2} style={{ maxWidth: 650, margin: 0, color: token.colorWhite }}>
              Tìm một khoảng trời riêng tại Đà Nẵng
            </Title>
            <Button ghost href="#booking">
              Đặt phòng ngay
            </Button>
          </div>
        </section>
      </main>
      <MainFooter />
    </div>
  );
}

interface ExperienceCardProps {
  kicker: string;
  title: string;
  description: string;
  image: string;
  imageAlt: string;
  action: string;
  reverse?: boolean;
}

function ExperienceCard(props: ExperienceCardProps) {
  return (
    <article
      className={props.reverse ? 'experience-card experience-card--reverse' : 'experience-card'}
    >
      <div className="experience-copy">
        <span className="home-kicker">{props.kicker}</span>
        <h2>{props.title}</h2>
        <p>{props.description}</p>
        <Button href="#contact" icon={<ArrowRightOutlined />} iconPlacement="end">
          {props.action}
        </Button>
      </div>
      <img
        className="experience-image"
        src={props.image}
        alt={props.imageAlt}
        loading="lazy"
        decoding="async"
      />
    </article>
  );
}
