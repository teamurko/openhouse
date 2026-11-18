# OpenHouse: Iceberg 1.5.2 fork to 1.12.0

**Assessment date:** 2026-11-18  
**Goal:** move the Tables service and default Spark 3.5 stack to an Apache Iceberg
1.12.0-based distribution, preserve LinkedIn-specific behavior, and interoperate
with LinkedIn's actual Trino deployment.

This is a source-backed assessment and implementation plan, not an implemented or
runtime-certified upgrade. The report includes internal deployment and dependency
evidence; review it before publishing outside LinkedIn.

## 1. Recommendation

**Proceed as a compatibility-first platform upgrade, not a dependency bump or a
table-format migration.**

Use Spark **3.5 / Scala 2.12 / Java 17** as the default lane. Upgrade the service,
modern Java catalog, Spark runtime, and maintenance applications together through
versioned candidate artifacts. Keep existing tables at **format v2**, and preserve
v1 tables where they exist. Do not automatically enable v3/v4, deletion vectors,
new types, or native default-value metadata.

The largest findings are:

1. **The deployed Trino baseline is 446.279.0, not the 406 JDBC dependency in
   li-openhouse.** Live Holdem and War queries confirmed it. The current Trino
   source pins Apache Iceberg **1.6.1**, and trino-plugins explicitly excludes
   `com.linkedin.iceberg`. Thus Trino does not simply run the same fork as Spark.
2. **Stock 1.12 is not a superset of the LinkedIn fork.** In particular, its ORC
   projection still rejects missing fields with non-null defaults; LinkedIn has
   custom handling for those reads. Several other fork-specific APIs are absent.
3. **The existing v2 read bridge conflicts with newer schema-validation rules.**
   Apache requires format v3 for non-null `initial-default`. Preserve the bridge
   through a scoped compatibility design; do not upgrade tables to v3 to make a
   validation failure disappear.
4. **Spark wrappers depend on Iceberg implementation details.** The CVL adapter
   calls a fork-only `SparkScanBuilder.includeColumnStats(Collection)` method and
   uses package-private scan/write classes. A successful service build is not
   sufficient evidence that the Spark client works.
5. **Java, Hadoop, and packaging are first-class work.** Target Iceberg requires
   Java 17 and no longer supports Hadoop 2. OpenHouse still declares Java 8 bytecode,
   has Hadoop 2-era dependencies, pins `iceberg-aws` separately to `1.2.0.6`, and
   contains copied Azure classes.

The recommended first delivery consists of **nine projects, approximately 21-30
engineer-weeks**, before contingency or external platform upgrades. This is a
planning estimate, not a calendar commitment. Trino's own Iceberg convergence and
new table-format adoption are separate, optional projects.

## 2. Evidence and current baseline

### Source identities

| Repository | Inspected branch/revision | Meaning |
| --- | --- | --- |
| `li-iceberg` | `openhouse-1.5.2`, `af77f055000f8e7940f1e819f37c5c317e98f835` | Exactly the commit tagged `v1.5.2.24` |
| `iceberg` | `apache-iceberg-1.12.0`, `212aa894e919ff9fdf89160da4aadcbe6858ac01` | Exactly the dereferenced Apache `apache-iceberg-1.12.0` tag |
| `openhouse` | `main`, `6122f5f729609a6cf8a22fe7dd6b94f476498a71` | Local integration source baseline |
| `li-openhouse` | `stpak_add_ac_test`, `6da5c66fc83b7be43e1f09ba5105af0756fde133` | Local checkout, not a claim about its deployment |
| `trino-plugins` | Default-branch commit `bccc61e76d2ec2568eae079d3eea410a589e85da` | Remote source read through GitHub |
| LinkedIn Trino fork | `li-446`, `4a979e1e5ed0f67ef92855e10f4f0233126f7db2` | Remote source read through GitHub |

The fork is shallow, but its available history reaches the exact Apache 1.5.2
base, `cbb853073e681b4075d7c8707610dceecbee3a82`. There are **29 commits after
that base**, touching **130 files**. The Apache 1.5.2-to-1.12.0 history contains
**3,931 commits**. This is a substantial implementation evolution, even though
both releases are in the 1.x public API family.

Existing untracked files in the source checkouts were not used as evidence.
No branches, dependencies, deployment configurations, or table data were changed.

### What the integrations declare today

| Component | Baseline found | Important qualification |
| --- | --- | --- |
| Tables service and internal catalog | LinkedIn Iceberg `1.5.2.24` | `services/tables` and `internalcatalog` select the 1.5 convention |
| Iceberg-backed HouseTables catalog | LinkedIn Iceberg `1.5.2.24` | Also directly depends on APIs removed in the target |
| Spark 3.1 lane | LinkedIn Iceberg `1.2.0.25` | Remains a separate compatibility lane |
| Spark 3.5 lane | LinkedIn Iceberg `1.5.2.24` through OpenHouse | li-openhouse also has a direct compile-only fork pin at `1.5.2.22` |
| li-openhouse's OpenHouse release | `0.5.561` | A source pin, not a deployed-image observation |
| LinkedIn Spark compile dependency | `com.linkedin.spark:spark-sql_2.12:3.5.2.11` | Target Apache Iceberg's Spark 3.5 build uses Spark `3.5.9` |
| Hadoop in li-openhouse | LinkedIn `2.100.1247.0` | Do not interpret this as supported upstream Hadoop 3 |
| Iceberg AWS dependency | `com.linkedin.iceberg:iceberg-aws:1.2.0.6` | Independently pinned despite the service's 1.5 core |
| Azure integration | Copied `org.apache.iceberg.azure.*` source plus Azure SDKs | Not an ordinary coordinated `iceberg-azure` dependency |

Sources: [O1], [O2], [O3], [O8], [O9].

The rendered li-openhouse jobs configurations already select Spark `3.5.2`,
including production and DR. However, `GridGatewayJobsCoordinator` still falls
back to `3.1.1` when the launch configuration omits a version. "Spark 3.5 by default"
therefore requires aligning fallback behavior, launch configuration, artifact
selection, documentation, and tests, not merely publishing a new runtime. [O9]

### Trino: measured deployment versus source dependencies

| Evidence | Result | Confidence / limitation |
| --- | --- | --- |
| Holdem, live `SELECT version()` | `446.279.0` | Measured in this assessment |
| War, live `SELECT version()` | `446.279.0` | Measured in this assessment |
| War, `system.runtime.nodes` grouped by version | 20 nodes, all `446.279.0` | Covers the nodes returned by that query |
| Faro | Not established | Cluster-specific SSO failed; cached-auth check also failed |
| li-openhouse `trino-mp` dependency | `406.277.5` | JDBC/SPI/client build dependency; not the server version |
| trino-plugins source `trino-mp` pin | `446.279.0` | Matches the observed production server version |
| trino-plugins source OpenHouse SDK pin | `li-openhouse:4.2.248`, Java Iceberg-1.5 runtime | Source dependency, not verified deployed plugin archive |
| Trino fork root POM | `dep.iceberg.version=1.6.1`, target JDK 21 | Native connector source baseline |
| trino-plugins root build | Excludes group `com.linkedin.iceberg` | Deliberately prefers Trino's Apache Iceberg classes |

The OpenHouse connector imports ordinary `org.apache.iceberg.*` classes, obtains
`OpenHouseTableOperations` from the Java catalog, and wraps it in its own
`BaseTable`. These objects share a binary boundary inside the connector. Adding
an unshaded 1.12 catalog JAR to this classpath is **not** a safe independent upgrade.
Sources: [T1]-[T4].

**Recommended initial Trino contract:** retain deployed Trino 446 and its current
Iceberg-compatible Java catalog lane while certifying it against the upgraded
Tables service and Spark-produced v2 tables. Separate processes need compatible
metadata and wire semantics, not identical Iceberg JAR versions. Within a plugin
classloader, dependencies must remain consistent.

The exact deployed trino-plugins artifact and resolved connector JAR inventory
were not retrieved. The `1.6.1` conclusion is source-backed, not an inspection of
the live worker filesystem. Capturing that inventory is a release prerequisite.

Reproducible read-only evidence:

```sql
-- Holdem and War: both returned 446.279.0
SELECT version() AS trino_version;

-- War: returned one row, node_version=446.279.0, nodes=20
SELECT node_version, count(*) AS nodes
FROM system.runtime.nodes
GROUP BY node_version;

-- Holdem: captured the current query ID together with version()
SELECT query_id, version() AS trino_version
FROM system.runtime.queries
WHERE "user" = current_user
  AND query LIKE 'SELECT query_id, version() AS trino_version%';
```

The final Holdem query returned
`20261118_185929_19575_ramrw`, version `446.279.0`.
CLI invocation: `trino query --sso -c <holdem|war> -o jsonl '<SQL>'`.
No user-table queries or mutations were needed.

## 3. What changed upstream

This section describes material changes relevant to OpenHouse, not every upstream
commit. Release-note features are not assumed to be implemented in every engine.

| Release band | Material changes | OpenHouse consequence |
| --- | --- | --- |
| 1.6 | Wide-schema position-delete fixes; manifest/planning resource fixes; safer cleanup after failed writes; more commit/environment metadata | Several fixes are already backported into the LinkedIn fork; avoid reapplying obsolete code |
| 1.7 | Java 11 baseline; default-value APIs; initial v3 and row-lineage foundations; more maintenance/statistics capabilities | Defaults are no longer just local extensions; Java 8 stops being a supported runtime |
| 1.8 | Deletion-vector read/write building blocks; variant foundations; default readers for Parquet/Avro; REST auth refactoring; snapshot-expiration changes | Introduces new metadata/content-file shapes and cleanup behavior, but does not justify enabling them during the binary upgrade |
| 1.9 | Hadoop 2 removal; default-aware schema updates; required v3 row lineage; nanosecond timestamps/new types; planner/runner separation for rewrites | Major dependencies and compaction internals change; types remain engine-dependent |
| 1.10 | More DV lifecycle handling, partition statistics, REST commit/retry changes, Spark 4 support, new rewrite planning controls | Preserve OpenHouse's custom commit and cleanup semantics; an upstream REST change does not automatically alter OpenHouse's REST API |
| 1.11 | Java 17 baseline; format-model/file-writer refactoring; remote scan planning; additional vectorized readers, view APIs and maintenance behavior | Runtime/toolchain upgrade is mandatory; catalog and file-writer implementations need adaptation |
| 1.12 target source | Spark 3.4 removed; deprecated writer/core APIs removed; more v4 manifest machinery; contextual catalog loading; Spark 3.5 backports for scan/maintenance/view behavior | Concrete API removals and Spark adapter changes, even while staying on Spark 3.5 and table format v2 |

The checked-out `site/docs/releases.md` has detailed release sections through
1.11 but no 1.12 section. The 1.12-specific findings above are grounded in the
tagged source and its commit history, not inferred from stale documentation. [A1]

### Runtime and dependency changes

| Dependency / platform | LinkedIn 1.5 fork source | Apache 1.12 target source |
| --- | --- | --- |
| Java | Older-compatible source line | Java 17 bytecode; build accepts JDK 17 or 21 |
| Spark 3.5 test/build baseline | `3.5.0` in fork catalog | `3.5.9` |
| Supported Spark branches | 3.3, 3.4, 3.5 in this fork | 3.5, 4.0, 4.1, 4.2 |
| Default Spark build selection | Must inspect chosen invocation | Target defaults to 4.2; explicitly select 3.5 |
| Hadoop | Hadoop 2.7.3 / Hadoop 3 client 3.3.6 catalog entries | Hadoop 3.4.3 |
| Avro | 1.11.4 | 1.12.2 |
| Parquet | 1.13.1 | 1.17.1 |
| ORC | 1.9.2 | 1.9.9 |
| Arrow | 15.0.0 | 15.0.2 |
| Caffeine | 2.9.3 | 3.2.4 |
| Guava | 33.0.0-jre | 33.7.1-jre |
| SLF4J | 1.7.36 | 2.0.18 |
| Jackson | General BOM 2.14.2, engine-specific overrides | General BOM 2.22.2; Spark 3.5 forces core/databind/Scala module to 2.18.8 |

These are source declarations, not a resolved deployment SBOM. OpenHouse,
li-openhouse, Spark, and Trino each add exclusions, overrides, shading, and
provided libraries. A single universal BOM would be misleading. [A2], [F1], [O3]

Spark 3.5 remains an explicit target in 1.12, including Scala 2.12. That does
**not** certify LinkedIn Spark `3.5.2.11` against code tested upstream with 3.5.9.
Run the actual LinkedIn engine as a required matrix entry. Do not silently upgrade
the whole Spark platform as part of an Iceberg change.

### Library version is not table-format version

The old fork defaults to format **2** and accepts up to **2**. The target still
defaults to **2**, but its `TableMetadata` accepts versions up to **4**. This is
not proof that every v4 write/read path or engine is ready. [A3]

For the first rollout:

- Retain existing v1/v2 metadata and ordinary supported types.
- Reject unapproved format upgrades on service commit paths, including clients
  that explicitly request them. A default of v2 alone is not enforcement.
- Do not emit v3 deletion vectors, row-lineage fields, native defaults, or new
  types into shared tables.
- Keep the current column-default capability gate. Unknown
  `required-capability.*` names are currently ignored; merely adding a property
  does not create a new effective gate.
- Continue testing old readers and writers after new writers have committed.
  Reading old data with a new client is only half of the compatibility contract.

Spark 3.5's target type converter still has no handling for variant, geometry,
geography, or nanosecond timestamp types and rejects unsupported types. Iceberg's
new type system is therefore not a Spark 3.5 feature checklist. [A4]

## 4. Disposition of LinkedIn fork changes

"Retire" below means stop carrying the old implementation after equivalent
regression tests pass. It does not mean deleting behavior because an upstream
commit has a similar title.

| Fork change / representative commit | Target evidence | Recommended disposition / project |
| --- | --- | --- |
| Wide-schema metadata/position-delete ID remapping and MoR fixes, `6c9a0ec78`, `af77f0550` | Target has `Schema.idsToOriginal`, `PartitionSpec.rawPartitionType`, and corresponding manifest handling | Retire backport code after wide-schema, partition-evolution and MoR tests; P4/P7 |
| ParallelIterable memory fixes, `198cc0189` | Upstream 1.6+ contains the backported fixes and later changes | Use upstream implementation; retain close/cancel/bounded-memory cases; P1/P7 |
| Null-partition conflict handling, `c3a07e2ef` | Upstream fix is part of the intervening history | Retire backport after concurrent overwrite tests; P4 |
| CachingCatalog metadata-table loading, `3d6d0d7c1` | Upstream-origin fix is in the later line | Verify base/metadata-table cache behavior before dropping patch; P4 |
| App name in snapshots/environment context, `0b10d8734`, `e1103d86c` | Target has `CatalogProperties.APP_NAME` and Spark catalog environment registration | Prefer upstream support; verify exact emitted audit/snapshot fields; P5 |
| Streamed orphan-file results, `c9c41c46f` | Target Spark 3.5 has `stream-results` and `max-orphan-file-sample-size` | Reuse upstream implementation after behavioral comparison, including bounded result samples; P7 |
| Delete-file replication, `25f1e5c9b`, `809534da0`, `dbcca9b96` | Target `FileIO` lacks the fork's output-properties overload and replication plumbing | Port or redesign the complete write path; metadata round-trip is not sufficient; P2 |
| Byte-budgeted rewrite and sequence-number ordering, `04d2cd2af` | Target lacks the fork's `max-total-files-size-bytes` and sequence-order enum values; newer rewrite code uses planners/runners | Reimplement the contract on the new architecture, not a mechanical cherry-pick; P7 |
| Compaction output-size correction, `a6aef6788` | Rewrite planning changed substantially | Compare output-size behavior and partial progress, then retain only what remains necessary; P7 |
| Spark SQL split-size property, `efb092202` | Fork key is `spark.sql.iceberg.split-size`; target key is `spark.sql.iceberg.read.split-size` | Provide an intentional alias/migration and preserve precedence; P5 |
| Partitioned unsorted write default NONE, `d69c1fd91` | Target defaults to HASH, while sorted tables use RANGE | Preserve LinkedIn's current default in the first rollout, preferably through scoped configuration; benchmark any later switch; P5 |
| View-command rewrite opt-out, `b7851bb63` | Target parser rewrites view commands unconditionally; fork adds `spark.sql.iceberg.rewriteViewCommands.enabled` | Retain the opt-out until LinkedIn Spark view compatibility is demonstrated; P5 |
| Default-value APIs/schema serialization, `d1603c807` | Upstream APIs exist, but compatibility rules differ | Reconcile API and wire semantics; do not copy the backport wholesale; P3 |
| ORC field-ID binding, `8cbd4f5a8` | Target `OrcValueReaders` also binds readers by field ID | Prefer upstream binding and retain reordered/nested field tests; P3 |
| ORC initial-default projection/filter behavior, `1b8361f00`, `4df974156`, `39df55d00` | Target `ORCSchemaUtil` explicitly throws for a missing field with a non-null initial default | Must retain/reimplement; test complete/partial/missing embedded IDs and filter pushdown; P3 |
| CVL statistics retention, `8a501e296` | Generic Scan API can include stats, but target SparkScanBuilder does not expose the fork method used by li-openhouse | Rebase the Spark hook and wrappers; generic API presence does not solve the call-site gap; P5 |
| Publishing/CI changes and Avro patch | Target has newer dependencies and different build machinery | Recreate minimal LinkedIn publication controls and provenance; do not replay historical CI fixes verbatim; P1 |

Primary evidence: fork diff against `cbb853073`, [F2]-[F5], [A5]-[A7].

## 5. Concrete OpenHouse integration work

### Default values and ORC are a release blocker

OpenHouse's `ReadBridge` loads raw metadata, overlays `initial-default` values
from server configuration, and reparses the metadata. Server policies validate
default-aware requests and strip read-bridge defaults before persistence.
This is a deliberate v2 extension, not native Apache v3 defaults. [O4]

In the target, `Schema.checkCompatibility` rejects a non-null initial default
for format versions below 3, and `TableMetadata.Builder` invokes schema
compatibility checks. The failure must be evaluated at the actual builder and
commit call sites; not every raw metadata parse necessarily invokes that check.
Separately, ORC projection rejects the missing-field/default case outright. [A3], [A5]

The port must preserve the distinction between **read schema** and **persisted
schema**, including intermediate schemas and retry paths. Prefer a narrow
read-bridge adaptation over globally relaxing Apache's format validation.
Native v3 defaults are a later migration, with a separate rollback boundary.

Trino's Java catalog currently declares no column-default capability; the
service deliberately rejects unsupported accesses. Do not "fix" interoperability
by adding the capability header without implementing correct Trino row reads.

### Service/catalog APIs and serialization

The service uses custom `BaseMetastoreTableOperations`, custom HouseTables
storage, and OpenHouse-specific table/snapshot requests. It is not simply an
Apache RESTCatalog deployment. Preserve:

- Optimistic concurrency, stale-base rejection, and ambiguous commit handling.
- Pre-commit locks, authorization, capability checks, and property policies.
- Create/replace transactions, intermediate schemas, snapshot refs and branches.
- Metadata locations, cache invalidation, replication and post-commit audit.
- The rule that uncertain commit outcomes must not trigger destructive cleanup.

The target removes `GenericAppenderFactory`, which is used directly by
`htscatalog/.../IcebergDataUtils.java`. Adapt it to the new file-writer API with
explicit data/delete schemas and equality IDs. `GenericFileWriterFactory.Builder`
is available; do not assume the old construction or writer methods are retained.
[O5], [A6]

`libs/datalayout/.../DataCompactionConfig.java` imports removed
`BinPackStrategy` constants from the older lane. Because Spark 3.5 applications
reuse that shared library, it needs an intentional cross-version design rather
than relying on an old transitive JAR. Generated `Immutable*` types and
locally supplied package classes must not be mistaken for removed upstream APIs.

### Spark adapters and lineage

The li-openhouse Spark 3.5 integration wraps package-private Iceberg classes in
the `org.apache.iceberg.spark.source` package. The target uses newer `BatchScan`
paths and implements additional mix-ins such as limit pushdown.

Port the adapters against the actual target implementations and retain:

- Runtime filtering/dynamic partition pruning before task enumeration.
- Partitioning/statistics reporting and applicable limit/aggregate pushdown.
- Required write distribution and ordering.
- Branch/time-travel snapshot identity.
- CVL exact/superset/no-coverage semantics and runtime fallback.
- Existing intentional limits: MoR `SupportsDelta` operations currently bypass
  the CoW lineage wrapper; the upgrade must not claim new MoR lineage coverage.

The 3.5 module excludes inherited 3.1 lineage-adapter tests. Add target-specific
coverage and run the on-cluster matrix; a green inherited unit suite can miss
the main integration risk. [O6]

### Packaging and publication

Do not mix stock Apache and LinkedIn-fork classes with the same package names in
one loader, and do not mix a standalone Iceberg core with a different bundled
Spark runtime.

The existing shading logic deliberately excludes standalone core to avoid
`AvroSchemaUtil` signatures winning from the wrong JAR. Reassess that constraint
against target nested shading, service descriptors, Jackson/Avro, native
libraries, and multi-release entries. Inspect both thin and shaded artifacts.
Keep Spark/Hadoop dependencies provided where the deployment provides them. [O3]

Two publication traps are visible:

1. Fork `version.properties` contains `1.5.2.901`; the checked-out release is
   established by the `v1.5.2.24` tag, not that placeholder.
2. Apache `getProjectVersion()` uses `version.txt` when present, otherwise derives
   a next-minor `SNAPSHOT` from Git tags. Checking out the 1.12 tag does not by
   itself establish the version a local publish will use.

Pin source SHA, publication version, dependency manifests and runtime build
metadata explicitly. Update the version-line-aware Renovate rules when adding
the new lane; do not collapse the retained 1.2 lane into it. [A2], [O8]

## 6. Target compatibility contract

| Lane after initial rollout | Engine / JVM | Iceberg and artifact strategy | Table contract |
| --- | --- | --- | --- |
| Tables/internal catalog/HouseTables | Service Java 17 | Coordinated 1.12-based fork modules and LI extensions | Existing v1/v2 preserved; new tables v2 |
| Default Spark | LinkedIn Spark 3.5, Scala 2.12, Java 17 | New versioned 1.12 Java catalog and Spark 3.5 runtime release | v2, existing supported types and fork semantics |
| Legacy Spark | Existing Spark 3.1 deployment | Retained 1.2.0.25-compatible lane; no new feature work | Existing compatible v1/v2 workloads |
| Production Trino | Measured 446.279.0; source target JDK 21 | Retain currently compatible Java catalog lane and native connector Iceberg baseline initially | Certified v2 interoperability; unsupported default-aware tables remain gated |

Use clearly named modern Java artifacts, for example
`openhouse-java-iceberg-1.12-runtime` and its LI counterpart. Do not publish
1.12 bytecode under a Java artifact whose identity promises Iceberg 1.5.
The existing Spark 3.5 artifact name can receive a new, explicitly selected
release after compatibility gates; never replace an existing immutable version.

Retaining a Trino-compatible Java lane need not imply a third permanent Spark
runtime. During canarying there will be old and candidate releases of Spark 3.5;
afterward the supported Spark lanes remain legacy 3.1 and default 3.5.

Do not remove shared 1.2 source directories while modern modules still inherit
them. Version-specific implementations should follow the repository's existing
`src/iceberg-version-specific/java` pattern.

## 7. Mid-sized implementation projects

Estimates below are **engineer-weeks**, including focused tests and review.
They assume access to the existing build/test infrastructure and exclude a
company-wide Hadoop/Spark/Trino platform upgrade. Dependencies describe required
integration deliverables; design and test preparation can overlap.

### P0. Establish the compatibility contract and baseline

**Owner:** OpenHouse lead with Spark, storage and Trino owners.  
**Size:** 1-2 engineer-weeks. **Dependencies:** none.

**Scope:** inventory actual Tables images, client artifacts, Spark/JVM tuples and
Trino plugin JARs; classify table formats and default-enabled cohorts; baseline
representative correctness/performance tests. Assign a keep/retire/reimplement
decision and regression test to all 29 fork commits. Define the support and
deprecation window for old clients.

**Deliverable:** a versioned compatibility matrix, patch ledger, candidate GAV
scheme, and agreed performance/rollback thresholds.

**Done when:** each supported producer/consumer pair has an owner and test;
Faro access or an equivalent staging target is available; deployed plugin
inventory confirms or corrects the Trino source-derived baseline.

**Out of scope:** production format changes, default flips, or broad dependency
upgrades.

### P1. Build and publish a reproducible 1.12 candidate

**Owner:** Iceberg/OpenHouse build maintainers.  
**Size:** 2-3 engineer-weeks. **Dependencies:** P0.

**Scope:** branch from the pinned Apache tag; publish coordinated 1.12-based fork
modules and Spark 3.5/Scala 2.12 runtime; require Java 17 for modern artifacts;
retain legacy compilation lanes. Resolve AWS/Azure/core version skew, dependency
exclusions, SLF4J, Jackson/Avro and nested shading. Establish a supported Hadoop
client strategy and capture effective runtime dependencies per environment.

**Deliverable:** immutable candidate artifacts with build provenance and
duplicate-class/ABI checks; updated lane-aware release automation.

**Done when:** candidate service/client artifacts load under their real JVMs;
Spark runtime contains one intended Iceberg implementation; thin and shaded
artifacts expose the expected APIs/resources; reproducible GAVs match manifests.

**Out of scope:** blindly replacing grid Hadoop. If target Iceberg cannot run on
the current LinkedIn filesystem stack, raise a separately owned platform
prerequisite rather than claiming upstream Hadoop 2 support.

### P2. Preserve storage and delete-file durability behavior

**Owner:** Storage/FileIO maintainers.  
**Size:** 2-3 engineer-weeks. **Dependencies:** P1.

**Scope:** adapt HDFS/Blob/resolving FileIO and copied Azure integration to target
APIs; port per-output-file replication through factory, format writer and Hadoop
stream creation; preserve configuration precedence and no-option behavior.
Verify authentication, encrypted storage, URI handling and resource ownership.

**Deliverable:** a minimal, tested storage compatibility patch set and effective
filesystem dependency contract for service and Spark.

**Done when:** real HDFS block replication matches requested delete-file factors
after both writes and rewrites; ORC and Parquet paths are covered; Blob paths do
not accidentally interpret HDFS-only options; credentials refresh and stream
closure preserve existing behavior.

**Out of scope:** changing replication policy values or introducing Iceberg-native
table encryption/new storage backends.

### P3. Preserve v2 default-value and ORC semantics

**Owner:** Schema/read-path maintainers.  
**Size:** 3-4 engineer-weeks. **Dependencies:** P1.

**Scope:** adapt default-value APIs and read-bridge metadata processing without
persisting v3-only fields into v2 tables; port missing-field default handling in
generic and Spark ORC readers/projection/filtering. Preserve capability checks,
strip protection, intermediate-schema handling and retry behavior.

**Deliverable:** a documented read-schema versus persisted-schema boundary plus
ported focused regressions.

**Done when:** old ORC files return correct values for new fields, including
nested/null/reordered fields and partial/no embedded IDs; pushed and unpushed
filters agree; unsupported clients remain rejected; v2 persisted metadata remains
readable by supported old clients after create/append/evolve/replace/retry.

**Out of scope:** automatic v3 conversion, removing the default capability gate,
or implementing Trino default-aware row readers.

### P4. Port Tables, HouseTables and modern Java catalog APIs

**Owner:** Tables/catalog maintainers.  
**Size:** 3-4 engineer-weeks. **Dependencies:** P1, P3.

**Scope:** introduce the modern Java catalog lane; migrate removed file-writer
APIs; adapt custom table/view operations and metadata serializers; preserve
OpenHouse wire requests and failure semantics. Add explicit format/type gates
at relevant service boundaries. Keep the Trino-compatible Java lane buildable.

**Deliverable:** candidate Tables/HouseTables artifacts and matching LI service
extensions, plus a Java client compatibility suite.

**Done when:** old/new client and old/new service combinations satisfy the agreed
v2 contract; create/CTAS/replace/schema evolution/views/refs behave correctly;
concurrent and ambiguous commits neither lose updates nor clean committed files;
locks, ACLs, read-bridge policy, caches and post-commit events are unchanged.

**Out of scope:** replacing OpenHouse's REST API with Apache RESTCatalog, database
schema redesign, or redesigning authorization.

### P5. Port Spark 3.5 adapters and promote the default lane

**Owner:** Spark/OpenHouse client maintainers.  
**Size:** 3-4 engineer-weeks. **Dependencies:** P1-P4.

**Scope:** port scan/write/row-level wrappers and the CVL statistics-retention
hook; preserve Spark mix-ins and snapshot identity. Preserve current distribution
defaults and view-command opt-out, provide split-size property compatibility,
and align direct/indirect Iceberg pins. Update artifact selectors and the job
coordinator's fallback consistently with rendered Spark 3.5 configuration.

**Deliverable:** a Spark 3.5/Scala 2.12 candidate runtime, target-specific adapter
tests and a reversible default-selection change.

**Done when:** actual LinkedIn Spark passes read/write/DML/view/branch tests;
CVL and DPP behave as before; output files and shuffle behavior meet baseline;
the default path selects Spark 3.5 plus Java 17 plus the intended runtime;
an explicit legacy selector still works.

**Out of scope:** moving to Spark 4, retiring Spark 3.1 immediately, or adding new
MoR lineage capabilities.

### P6. Certify deployed Trino 446 interoperability

**Owner:** Trino OpenHouse connector maintainers, paired with catalog owners.  
**Size:** 3-4 engineer-weeks. **Dependencies:** P0, P1, P4.

**Scope:** validate the exact deployed plugin dependency graph; retain a compatible
Java catalog artifact rather than introducing 1.12 classes into a 1.6.1 loader.
Exercise the OpenHouse connector against upgraded service metadata and Spark
output. Update source pins/adapter code only where needed for the wire contract.

**Deliverable:** a certified Trino 446 connector/client tuple and consumer-build
gate for future OpenHouse releases.

**Done when:** bidirectional Spark/Trino create/append/read and supported DML,
schema evolution, partition evolution, refs, views, stats and error handling pass;
unsupported defaults/formats fail explicitly; query/commit behavior survives token
refresh and cache invalidation; no fork/upstream duplicate classes enter the plugin.

**Out of scope:** upgrading the Trino engine or its native Iceberg library to
1.12. That optional convergence is P9.

### P7. Port and qualify maintenance, optimizer and replication jobs

**Owner:** OpenHouse jobs/optimizer maintainers.  
**Size:** 2-3 engineer-weeks. **Dependencies:** P1, P2, P4.

**Scope:** migrate shared datalayout APIs; reimplement byte-budgeted and
sequence-ordered rewrites on target planners; validate output sizing, MoR and
delete rewriting. Preserve orphan-file sampling, backup/restore callbacks,
retention, snapshot expiration and replication safety.

**Deliverable:** candidate Spark 3.5 maintenance applications with operation-level
regressions and reviewed deletion semantics.

**Done when:** budgets are honored, wide-schema/partition-evolved tables compact
correctly, deletes remain effective, and partial progress is recoverable; GC never
removes files referenced by retained snapshots/branches or needed for the
supported backup/replication contract.

**Out of scope:** new clustering algorithms, changed retention/TTL defaults, or
enabling new file formats. Upstream Hilbert/Z-order capabilities are not required.

### P8. Cross-engine qualification and staged production rollout

**Owner:** OpenHouse release owner/SRE with Spark and Trino owners.  
**Size:** 2-3 engineer-weeks. **Dependencies:** P2-P7.

**Scope:** run the complete compatibility matrix using immutable packaged
artifacts; baseline correctness, latency, resource use and lineage; exercise
failure/retry and rollback. Roll out service, client defaults and maintenance
independently through a canary cohort.

**Deliverable:** signed-off compatibility results, dashboards/alerts, version
manifest, rollback runbook and a completed canary-to-production promotion.

**Done when:** canaries pass the agreed soak and representative maintenance/
replication cycles; old supported clients read/write new-produced v2 tables;
rollback succeeds without reversing metadata pointers or losing commits; no
unexplained correctness, lineage, latency or resource regressions remain.

**Out of scope:** fleet-wide table rewrites, blanket client retirement or new
format activation.

### Optional P9. Converge Trino's native connector on Iceberg 1.12

**Owner:** Trino team. **Size:** initial 3-5 engineer-week estimate, lower confidence.  
**Dependencies:** P1, P4, P6.

Keep the Trino 446 engine/SPI fixed initially, but port its Iceberg connector,
OpenHouse adapter and dependency graph together to 1.12. Recompile and qualify
against Trino's native readers/writers; do not substitute Spark's runtime JAR.
Deliver a plugin with one consistent Iceberg class universe and rerun connector
and cross-engine tests.

Completion is optional for the v2 service/Spark upgrade. If the 446 connector
cannot reasonably be ported, select a Trino-engine upgrade as a separately scoped
platform project. This estimate does not include that engine upgrade.

### Optional P10. Pilot one native v3 feature

**Owner:** Table-format/schema owners plus all affected engines.  
**Size:** 2-4 engineer-weeks for one bounded pilot, after feasibility is established.  
**Dependencies:** P8 and demonstrated engine support, potentially P9.

Choose one feature and an isolated table cohort, such as native defaults with
supported readers or deletion vectors for a demonstrated workload. Define
writers/readers, authorization, metadata preservation, maintenance support and
an explicit migration/restore plan.

Do not bundle variants, geospatial types, nanosecond timestamps, row lineage and
v4 manifests into one project. Spark 3.5 support constraints remain in force.
V4 adoption requires its own maturity and interoperability assessment.

## 8. Delivery ordering and rollout safeguards

The integration dependency graph is:

```text
P0 -> P1
P1 -> P2, P3
P1 + P3 -> P4
P1 + P2 + P3 + P4 -> P5
P0 + P1 + P4 -> P6
P1 + P2 + P4 -> P7
P2 + P3 + P4 + P5 + P6 + P7 -> P8

P6 + P1 + P4 -> P9 (optional Trino binary convergence)
P8 + proven engine support -> P10 (optional format-feature pilot)
```

P2 and P3 can progress independently once P1's candidate APIs/build are available.
Trino and maintenance test preparation can start early, but their integration
gates depend on the service/client contract. The 21-30 engineer-week estimate is
the sum for P0-P8, not elapsed time; storage-platform incompatibility and
unavailable staging access are unpriced external blockers.

Recommended promotion order:

1. **Freeze behavior and artifacts.** Establish baseline images/JARs, pin v2 and
   old-compatible semantics, and prevent unapproved new metadata features.
2. **Qualify packaged candidates in staging.** Include real LinkedIn Spark,
   current Trino, HDFS/Blob, service extensions and old clients, not only upstream
   unit tests.
3. **Canary the service while old clients remain in use.** Mixed service
   versions are allowed only after the bidirectional matrix passes.
4. **Canary explicit Spark 3.5 candidate users, then change defaults.** Keep
   legacy artifacts selectable. Verify both batch and long-lived clients.
5. **Promote maintenance separately.** Begin with representative isolated tables,
   audit planned/deleted paths, then expand retention/compaction/replication jobs.
6. **Expand after soak and rollback rehearsal.** Record the exact service,
   Spark, Java client and Trino plugin tuple.

### Rollback boundary

For the v2-only rollout, rollback means restoring compatible application/JAR
versions while preserving successfully committed table state. Pause affected
writers or maintenance as needed; never restore an old metadata pointer over
valid newer commits as a generic rollback mechanism.

Retaining format v2 is necessary but not sufficient: new schema/property/content
encodings must also remain understood by old clients. Test rollback after new
writers and maintenance jobs have produced output.

Once a table is upgraded to v3/v4 or uses unsupported new content/types, a JAR
rollback is no longer a valid recovery assumption. That is why P10 is separate.

## 9. Required qualification matrix

| Area | Minimum meaningful cases |
| --- | --- |
| Binary/package compatibility | Service boot JAR plus LI extensions; modern Java thin/uber JARs; Spark nested shading; Trino plugin classpath; actual JVM and provided Hadoop/Spark libraries |
| Producer/consumer skew | Old/new service with old/new Java/Spark clients; Spark 3.1 legacy; deployed Trino 446; read and write in both directions where supported |
| Table operations | Create, CTAS, replace/RTAS, append, schema/partition evolution, snapshots/refs, branches/tags, metadata tables and views |
| Row-level behavior | CoW and MoR update/delete/merge, equality and position deletes, compaction and rewrite-delete correctness |
| Schemas/defaults | More than 1,000 columns; evolved partition IDs; required/optional/nested/null/reordered fields; ORC embedded IDs absent/partial/complete; default filter pushdown versus full read |
| FileIO | ORC/Parquet/Avro as actually used; HDFS/Blob; replication factor measured on blocks, not just properties; encryption/credentials; stream cleanup |
| Service concurrency | Stale bases, concurrent commits, duplicate/retried requests, network timeout/unknown outcome, cache refresh, and no destructive cleanup of committed data |
| Maintenance | Budget limits and ordering; output-size distribution; partial progress; backup/restore; retained branches; streamed orphan samples; replica retention |
| Lineage/audit | CVL exact/superset/no-coverage; runtime fallback; DPP task selection; branch snapshot identity; audit fields; no newly claimed MoR support |
| Performance | Planning memory, manifest-heavy tables, scan p95, shuffle/spill, file counts, commit latency, GC duration; thresholds agreed against the P0 baseline |
| Gates/rollback | Rejected unsupported format/type/default operations; old clients on new output; service and runtime rollback after real commits |

Use existing focused tests first. Particularly valuable starting points include
`ReadBridgeColumnDefaultE2ETest`, `ReadBridgeStripProtectionTest`,
`ColumnDefaultCommitPolicyTest`, `SparkMoRFunctionalTest`,
`StreamResultsOrphanFilesDeletionTest`, and the fork's ORC/default/replication
regressions. Their fixtures and API bindings may need porting.

Example qualification commands, **proposed, not run in this assessment**:

```bash
# Target Iceberg: explicitly select Spark 3.5 rather than the target's 4.2 default.
./gradlew -DsparkVersions=3.5 -DscalaVersion=2.12 \
  :iceberg-core:test :iceberg-data:test :iceberg-orc:test \
  :iceberg-spark:iceberg-spark-3.5_2.12:test \
  :iceberg-spark:iceberg-spark-extensions-3.5_2.12:test

# OpenHouse service/catalog subset; run in the upgraded integration branch.
./gradlew :services:tables:test :services:housetables:test \
  :iceberg:openhouse:internalcatalog:test :iceberg:openhouse:htscatalog:test

# Existing li-openhouse Spark 3.5 and service-extension module names.
./gradlew :integrations:spark:spark-3.5:li-openhouse-spark-3.5-runtime_2.12:test \
  :services:tables-extensions:test

# trino-plugins: these integration tasks are explicitly defined in its build.
./gradlew :trino-openhouse:ohIntegrationTest \
  :trino-openhouse:ohIntegrationSmokeTest
```

Also run existing formatting/static checks and packaged/on-cluster integration
suites for each modified repository. Upstream RevAPI's comparison against 1.11
does not certify OpenHouse's internal-API usage against the 1.5 LinkedIn fork.

## 10. Sources and confidence

Local paths below are relative to `/Users/stpak/Code/`; revisions are recorded in
section 2. Line references describe those inspected revisions.

| ID | Evidence |
| --- | --- |
| A1 | `iceberg/site/docs/releases.md:69-855`; target history between `refs/tags/apache-iceberg-1.11.0` and `refs/tags/apache-iceberg-1.12.0`, including removals `1b27e2d7cc`, `8c9a75bf65`, `84c4339065`, `463249a44b` |
| A2 | `iceberg/gradle.properties:21-29`; `iceberg/gradle/libs.versions.toml:22-97`; `iceberg/build.gradle:54-81,285-286,1212-1231`; `iceberg/spark/v3.5/build.gradle:21-39` |
| A3 | `iceberg/core/src/main/java/org/apache/iceberg/TableMetadata.java:57-58,1621`; `iceberg/api/src/main/java/org/apache/iceberg/Schema.java:61,604-635`; old fork `TableMetadata.java:53-54` |
| A4 | `iceberg/spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/TypeToSparkType.java:94-133` |
| A5 | `iceberg/orc/src/main/java/org/apache/iceberg/orc/ORCSchemaUtil.java:398-414`; `OrcValueReaders.java:187-253` in the same package |
| A6 | `iceberg/data/src/main/java/org/apache/iceberg/data/GenericFileWriterFactory.java`; target removal commit `1b27e2d7cc` |
| A7 | `iceberg/spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java:74-90,409-483,701-766`; `SparkWriteConf.java:339-346`; `SparkSQLProperties.java:117`; target parser `IcebergSparkSqlExtensionsParser.scala:122` |
| F1 | `li-iceberg/gradle/libs.versions.toml:22-100`; `version.properties`; `v1.5.2.24^{commit}` |
| F2 | `li-iceberg/api/src/main/java/org/apache/iceberg/io/FileIO.java:74-90`; `core/.../TableProperties.java:375`; commits `25f1e5c9b`, `809534da0`, `dbcca9b96` |
| F3 | `li-iceberg/api/src/main/java/org/apache/iceberg/actions/RewriteDataFiles.java:73-76`; `RewriteJobOrder.java:45-46`; commits `04d2cd2af`, `a6aef6788` |
| F4 | `li-iceberg/spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java:159-166,532`; commits `8a501e296`, `d69c1fd91`, `b7851bb63`, `efb092202` |
| F5 | Fork commits `d1603c807`, `8cbd4f5a8`, `1b8361f00`, `4df974156`, `39df55d00`, `6c9a0ec78`, `af77f0550`; diff `cbb853073..af77f0550` |
| O1 | `openhouse/build.gradle:28-35`; `services/tables/build.gradle:6`; `iceberg/openhouse/internalcatalog/build.gradle:4`; `iceberg/openhouse/htscatalog/build.gradle:4` |
| O2 | `li-openhouse/product-spec.json:74,320,429-447,585-593`; `build.gradle:25,221-235` |
| O3 | `openhouse/buildSrc/src/main/groovy/openhouse.java-minimal-conventions.gradle:7-8`; AWS/Azure convention files; `integrations/java/iceberg-1.5/openhouse-java-runtime/build.gradle:21-65`; `li-openhouse/buildSrc/src/main/groovy/li-openhouse.spark-runtime-common.gradle:54-98` |
| O4 | `openhouse/integrations/java/iceberg-1.2/openhouse-java-runtime/src/main/java/com/linkedin/openhouse/javaclient/ReadBridge.java`; `services/tables/.../readbridge/{ClientCapabilityGate,ColumnDefaultCommitPolicy,ReadBridgeStripProtection}.java`; `SETUP.md:403-456` |
| O5 | `openhouse/iceberg/openhouse/htscatalog/src/main/java/com/linkedin/openhouse/hts/catalog/data/IcebergDataUtils.java:16,43-60`; `internalcatalog/.../OpenHouseInternalTableOperations.java`; `integrations/java/iceberg-1.2/.../OpenHouseTableOperations.java`; `libs/datalayout/.../DataCompactionConfig.java:6-27` |
| O6 | `li-openhouse/integrations/spark/spark-3.5/li-openhouse-spark-runtime/build.gradle:10-39,55-83`; version-specific `org/apache/iceberg/spark/source/{OpenHouseSparkScanBuilder,OpenHouseDataAccessScan,OpenHouseWrite,OpenHouseRowLevelOperationBuilder}.java` |
| O7 | `openhouse/apps/spark/src/main/java/com/linkedin/openhouse/jobs/spark/Operations.java:111-177`; `OrphanFilesDeletionSparkApp.java:96-115`; `apps/spark-3.5/build.gradle:8-34`; corresponding MoR/stream-results tests |
| O8 | `openhouse/settings.gradle:25-81`; `li-openhouse/settings.gradle:18-49`; `openhouse/docs/development/renovate-iceberg-sync.md:3-31` |
| O9 | `li-openhouse/extensions/src/main/java/com/linkedin/openhouse/jobs/service/GridGatewayJobsCoordinator.java:32,110-125`; `.linkedin/kube/openhouse-services/rendered-configs/{prod-ltx1/prod,prod-lva1/dr}/jobs.yaml:83` |
| T1 | [trino-plugins product-spec, pinned revision](https://github.com/linkedin-multiproduct/trino-plugins/blob/bccc61e76d2ec2568eae079d3eea410a589e85da/product-spec.json#L196-L203), and lines 246-284 for Trino |
| T2 | [trino-plugins fork exclusion](https://github.com/linkedin-multiproduct/trino-plugins/blob/bccc61e76d2ec2568eae079d3eea410a589e85da/build.gradle#L42-L44) |
| T3 | [OpenHouse connector build](https://github.com/linkedin-multiproduct/trino-plugins/blob/bccc61e76d2ec2568eae079d3eea410a589e85da/trino-openhouse/build.gradle#L34-L65), integration tasks at lines 151-171; [catalog binary boundary](https://github.com/linkedin-multiproduct/trino-plugins/blob/bccc61e76d2ec2568eae079d3eea410a589e85da/trino-openhouse/src/main/java/com/linkedin/trino/openhouse/client/catalog/TrinoOpenHouseCatalog.java#L599-L610) |
| T4 | [LinkedIn Trino POM](https://github.com/linkedin-grid/trino/blob/4a979e1e5ed0f67ef92855e10f4f0233126f7db2/pom.xml#L145-L198); `trino-mp/product-spec.json` identifies `linkedin-grid/trino@li-446` as its external source |

**Verified:** branch/tag identities, listed source dependencies, concrete API and
behavior differences, and live Holdem/War Trino versions.

**Not yet verified:** target compilation of OpenHouse/LI extensions, deployed
plugin JAR contents, Faro version, actual LinkedIn Spark/Hadoop runtime
compatibility, performance, or a safe mixed-version production rollout. These
are explicit project acceptance criteria rather than assumed outcomes.
