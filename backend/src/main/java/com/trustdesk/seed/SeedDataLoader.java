package com.trustdesk.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.customer.Customer;
import com.trustdesk.customer.CustomerRepository;
import com.trustdesk.eval.EvalCase;
import com.trustdesk.eval.EvalCaseRepository;
import com.trustdesk.kb.KbDocument;
import com.trustdesk.kb.KbDocumentRepository;
import com.trustdesk.kb.KbMarkdownParser;
import com.trustdesk.kb.KbMarkdownParser.ParsedKbDocument;
import com.trustdesk.order.Order;
import com.trustdesk.order.OrderRepository;
import com.trustdesk.seed.SeedRecords.CustomerSeed;
import com.trustdesk.seed.SeedRecords.EvalCaseSeed;
import com.trustdesk.seed.SeedRecords.OrderSeed;
import com.trustdesk.seed.SeedRecords.TicketSeed;
import com.trustdesk.seed.SeedRecords.ToolSeed;
import com.trustdesk.ticket.Ticket;
import com.trustdesk.ticket.TicketEvalLabel;
import com.trustdesk.ticket.TicketEvalLabelRepository;
import com.trustdesk.ticket.TicketRepository;
import com.trustdesk.tool.ToolDefinition;
import com.trustdesk.tool.ToolDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads the course seed data into PostgreSQL on every startup.
 * <p>
 * Safe to run repeatedly: every record keeps its ID from the source files, so saving an
 * existing ID updates the row instead of inserting a duplicate. The whole load is one
 * transaction, so a bad file leaves the database unchanged.
 */
@Component
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final Path dataDir;
    private final ObjectMapper objectMapper;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketEvalLabelRepository ticketEvalLabelRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final EvalCaseRepository evalCaseRepository;

    public SeedDataLoader(@Value("${trustdesk.data-dir}") String dataDir,
                          ObjectMapper objectMapper,
                          CustomerRepository customerRepository,
                          OrderRepository orderRepository,
                          TicketRepository ticketRepository,
                          TicketEvalLabelRepository ticketEvalLabelRepository,
                          ToolDefinitionRepository toolDefinitionRepository,
                          KbDocumentRepository kbDocumentRepository,
                          EvalCaseRepository evalCaseRepository) {
        this.dataDir = Path.of(dataDir).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.ticketEvalLabelRepository = ticketEvalLabelRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.kbDocumentRepository = kbDocumentRepository;
        this.evalCaseRepository = evalCaseRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!Files.isDirectory(dataDir)) {
            throw new IllegalStateException("Seed data folder not found: " + dataDir
                    + " (set trustdesk.data-dir or TRUSTDESK_DATA_DIR)");
        }
        log.info("Loading seed data from {}", dataDir);

        // Order matters: orders need customers, tickets need customers and orders.
        loadCustomers();
        loadOrders();
        loadTickets();
        loadTools();
        loadKnowledgeBase();
        loadEvalCases();

        log.info("Seed data loaded: {} customers, {} orders, {} tickets, {} eval labels, {} tools, {} kb documents, "
                        + "{} eval cases",
                customerRepository.count(), orderRepository.count(), ticketRepository.count(),
                ticketEvalLabelRepository.count(), toolDefinitionRepository.count(), kbDocumentRepository.count(),
                evalCaseRepository.count());
    }

    private void loadCustomers() throws IOException {
        for (CustomerSeed seed : readJson("customers.json", new TypeReference<List<CustomerSeed>>() { })) {
            customerRepository.save(new Customer(seed.customerId(), seed.name(), seed.email(), seed.tier(),
                    seed.country(), seed.createdAt(), seed.verified(), seed.tags()));
        }
    }

    private void loadOrders() throws IOException {
        for (OrderSeed seed : readJson("orders.json", new TypeReference<List<OrderSeed>>() { })) {
            Customer customer = findCustomer(seed.customerId(), "order " + seed.orderId());
            orderRepository.save(new Order(seed.orderId(), customer, seed.status(), seed.placedAt(),
                    seed.deliveredAt(), seed.eligibleReturnUntil(), seed.total(), seed.currency(),
                    seed.paymentStatus(), seed.trackingNumber(), seed.items()));
        }
    }

    private void loadTickets() throws IOException {
        for (TicketSeed seed : readJson("tickets.json", new TypeReference<List<TicketSeed>>() { })) {
            Customer customer = findCustomer(seed.customerId(), "ticket " + seed.ticketId());
            Order order = seed.orderId() == null ? null : orderRepository.findById(seed.orderId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Ticket " + seed.ticketId() + " references unknown order " + seed.orderId()));

            Ticket ticket = ticketRepository.save(new Ticket(seed.ticketId(), customer, order, seed.channel(),
                    seed.subject(), seed.body(), seed.createdAt(), seed.status()));

            // The label shares the ticket's ID (@MapsId), so update an existing row or create a new one.
            TicketEvalLabel label = ticketEvalLabelRepository.findById(ticket.getTicketId())
                    .orElseGet(() -> new TicketEvalLabel(ticket));
            label.update(seed.expectedCategory(), seed.expectedPriority(), seed.expectedSentiment(),
                    seed.expectedEscalation(), seed.expectedActions());
            ticketEvalLabelRepository.save(label);
        }
    }

    private void loadTools() throws IOException {
        for (ToolSeed seed : readJson("tool_actions.json", new TypeReference<List<ToolSeed>>() { })) {
            toolDefinitionRepository.save(new ToolDefinition(seed.toolName(), seed.description(), seed.riskLevel(),
                    seed.requiresHumanApproval(), seed.allowedCategories(), seed.requiredFields(),
                    seed.maxAmountInr()));
        }
    }

    private void loadKnowledgeBase() throws IOException {
        Set<String> seenDocIds = new HashSet<>();
        List<Path> files;
        try (Stream<Path> paths = Files.list(dataDir.resolve("knowledge_base"))) {
            files = paths.filter(path -> path.toString().endsWith(".md")).sorted().toList();
        }

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            ParsedKbDocument parsed = KbMarkdownParser.parse(file.getFileName().toString(), content);

            if (!seenDocIds.add(parsed.docId())) {
                throw new IllegalStateException("Duplicate Doc ID " + parsed.docId() + " in " + file);
            }

            // Unchanged file: keep the existing row and its updated_at timestamp.
            Optional<KbDocument> existing = kbDocumentRepository.findById(parsed.docId());
            if (existing.isPresent() && existing.get().getChecksum().equals(parsed.checksum())) {
                continue;
            }

            String sourcePath = "data/" + dataDir.relativize(file).toString().replace('\\', '/');
            kbDocumentRepository.save(new KbDocument(parsed.docId(), parsed.title(), parsed.audience(),
                    parsed.version(), parsed.content(), sourcePath, parsed.checksum(), OffsetDateTime.now()));
        }
    }

    /** eval_cases.jsonl: one JSON object per line; blank lines are skipped. */
    private void loadEvalCases() throws IOException {
        Set<String> seenCaseIds = new HashSet<>();
        for (String line : Files.readAllLines(dataDir.resolve("eval_cases.jsonl"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            EvalCaseSeed seed = objectMapper.readValue(line, EvalCaseSeed.class);
            if (!seenCaseIds.add(seed.caseId())) {
                throw new IllegalStateException("Duplicate case_id " + seed.caseId() + " in eval_cases.jsonl");
            }
            Ticket ticket = ticketRepository.findById(seed.ticketId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Eval case " + seed.caseId() + " references unknown ticket " + seed.ticketId()));
            evalCaseRepository.save(new EvalCase(seed.caseId(), ticket, seed.input(), seed.expected()));
        }
    }

    private <T> List<T> readJson(String fileName, TypeReference<List<T>> type) throws IOException {
        return objectMapper.readValue(dataDir.resolve(fileName).toFile(), type);
    }

    private Customer findCustomer(String customerId, String referencedBy) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException(
                        referencedBy + " references unknown customer " + customerId));
    }
}