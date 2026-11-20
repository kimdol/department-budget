package kr.budget.portal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public final class PortalImport {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> FIELDS = List.of("eventId","workspaceId","claimId","sequenceNo","eventType","amount");
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    record Event(String eventId,String workspaceId,String claimId,int sequenceNo,String eventType,long amount) {
        Event {
            for (String id : List.of(eventId,workspaceId,claimId)) {
                if (!ID.matcher(id).matches()) throw new IllegalArgumentException("식별자 형식 오류");
            }
            boolean allowed = switch (sequenceNo) {
                case 1 -> "APPROVAL".equals(eventType);
                case 2 -> Set.of("SETTLEMENT","CANCELLATION").contains(eventType);
                default -> false;
            };
            if (!allowed || amount < 0 || amount > 999999999999L)
                throw new IllegalArgumentException("메시지 차수·유형·금액 오류");
        }
    }

    private static Event fromJson(byte[] input) throws Exception {
        JsonNode node = JSON.readTree(input);
        if (node == null || !node.isObject() || node.size() != FIELDS.size()
                || !FIELDS.stream().allMatch(node::has)
                || !node.get("sequenceNo").isIntegralNumber() || !node.get("sequenceNo").canConvertToInt()
                || !node.get("amount").isIntegralNumber() || !node.get("amount").canConvertToLong())
            throw new IllegalArgumentException("JSON 필드 오류");
        return new Event(node.get("eventId").textValue(),node.get("workspaceId").textValue(),
                node.get("claimId").textValue(),node.get("sequenceNo").intValue(),
                node.get("eventType").textValue(),node.get("amount").longValue());
    }

    private static Event fromCsv(List<?> rows) {
        if (rows.size() != 1 || !(rows.getFirst() instanceof List<?> row) || row.size() != FIELDS.size())
            throw new IllegalArgumentException("CSV 한 행에 여섯 필드가 필요합니다.");
        return new Event((String)row.get(0),(String)row.get(1),(String)row.get(2),
                Integer.parseInt((String)row.get(3)),(String)row.get(4),Long.parseLong((String)row.get(5)));
    }

    static DefaultCamelContext routes() throws Exception {
        var camel = new DefaultCamelContext();
        camel.addRoutes(new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());
                from("direct:json").process(e -> e.getMessage().setBody(fromJson(e.getMessage().getBody(byte[].class))))
                        .to("direct:canonical");
                from("direct:csv").unmarshal().csv()
                        .process(e -> e.getMessage().setBody(fromCsv(e.getMessage().getBody(List.class))))
                        .to("direct:canonical");
                from("direct:canonical").process(e -> e.getMessage().setBody(JSON.writeValueAsBytes(e.getMessage().getBody(Event.class))));
            }
        });
        return camel;
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1 || !Set.of("csv","json").contains(args[0]))
                throw new IllegalArgumentException();
            byte[] input = System.in.readNBytes(8193);
            if (input.length > 8192) throw new IllegalArgumentException();
            try (var camel = routes()) {
                camel.start();
                try (var producer = camel.createProducerTemplate()) {
                    System.out.write(producer.requestBody("direct:"+args[0],input,byte[].class));
                }
            }
        } catch (Exception failure) {
            System.err.println("포털 메시지 변환 실패. 형식과 업무 필드를 확인하세요.");
            System.exit(1);
        }
    }
}
