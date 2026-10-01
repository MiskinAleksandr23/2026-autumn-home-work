package company.vk.edu.distrib.compute.sovesti.urlshortener.handler;

public interface ExchangeAttribute<T> {

    String key();

    Class<T> type();

    final class BodyAttribute implements ExchangeAttribute<ResponseBody> {

        @Override
        public String key() {
            return "response_body";
        }

        @Override
        public Class<ResponseBody> type() {
            return ResponseBody.class;
        }

    }

    final class StatusAttribute implements ExchangeAttribute<Integer> {

        @Override
        public String key() {
            return "response_status";
        }

        @Override
        public Class<Integer> type() {
            return Integer.class;
        }

    }
}
