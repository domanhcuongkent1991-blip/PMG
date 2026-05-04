#!/usr/bin/env node
const fs = require("fs");
const path = require("path");

const roleToProperty = {
  DEVICE_MASTER: "SHEETS_DEVICE_MASTER_SHEET_ID",
  DMBT_LOG: "SHEETS_DMBT_LOG_SHEET_ID",
  HGT_CHECKS: "SHEETS_HGT_CHECKS_SHEET_ID",
  LOOKUP_OPTIONS: "SHEETS_LOOKUP_OPTIONS_SHEET_ID",
  APP_CONFIG: "SHEETS_APP_CONFIG_SHEET_ID",
};

function parseArgs(argv) {
  const args = {
    projectRoot: path.resolve(__dirname, ".."),
    offline: false,
    json: false,
    outputPath: "",
    selfTest: false,
    includeRowStats: false,
  };
  for (let index = 2; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--project-root") {
      args.projectRoot = path.resolve(argv[++index]);
    } else if (arg === "--offline") {
      args.offline = true;
    } else if (arg === "--json") {
      args.json = true;
    } else if (arg === "--output") {
      args.outputPath = path.resolve(argv[++index]);
    } else if (arg === "--self-test") {
      args.selfTest = true;
    } else if (arg === "--include-row-stats") {
      args.includeRowStats = true;
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return args;
}

function normalizeHeader(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/\s+/g, " ");
}

const headerAliasesByRole = {
  DMBT_LOG: {
    ma_thiet_bi: ["ma_thiet_bi", "ma thiet bi", "thiet bi", "ma tb"],
    ngay_phat_hien: ["ngay_phat_hien", "ngay phat hien"],
    ngay_sua_chua: ["ngay_sua_chua", "ngay sua chua"],
  },
  HGT_CHECKS: {
    ma_thiet_bi: ["ma_thiet_bi", "ma thiet bi", "thiet bi"],
    chu_ky_ngay: ["chu_ky_ngay", "chu ky ngay", "chu ki(ngay)", "chu ki (ngay)", "chu ky(ngay)"],
    lan_gan_nhat: ["lan_gan_nhat", "lan gan nhat"],
    lan_tiep_theo: ["lan_tiep_theo", "lan tiep theo"],
  },
  DEVICE_MASTER: {
    device_code: ["device_code", "ma thiet bi", "thiet bi"],
  },
  LOOKUP_OPTIONS: {
    option_group: ["option_group", "nhom", "group"],
    option_key: ["option_key", "key", "ma"],
    option_label: ["option_label", "label", "ten hien thi"],
  },
  APP_CONFIG: {
    config_key: ["config_key", "key"],
    config_value: ["config_value", "value", "gia tri"],
    value_type: ["value_type", "type", "kieu"],
  },
};

function rowContainsAlias(normalizedRow, aliases) {
  return aliases.some((alias) => normalizedRow.includes(normalizeHeader(alias)));
}

function scoreHeaderRow(role, row) {
  const aliases = headerAliasesByRole[role] || {};
  const normalizedRow = row.map(normalizeHeader).filter(Boolean);
  let score = 0;
  for (const aliasList of Object.values(aliases)) {
    if (rowContainsAlias(normalizedRow, aliasList)) score += 1;
  }
  return score;
}

function detectHeaderRow(role, rows) {
  const aliases = headerAliasesByRole[role] || {};
  const minimumScore = Math.max(1, Math.min(2, Object.keys(aliases).length));
  let best = { rowNumber: null, headers: [], score: 0 };
  rows.forEach((row, index) => {
    const headers = Array.isArray(row) ? row.map((value) => String(value)) : [];
    const score = scoreHeaderRow(role, headers);
    if (score > best.score) {
      best = { rowNumber: index + 1, headers, score };
    }
  });
  if (best.score >= minimumScore) return best;
  return { rowNumber: null, headers: [], score: best.score };
}

function headerIndex(headers, aliases) {
  const normalizedHeaders = headers.map(normalizeHeader);
  for (const alias of aliases) {
    const index = normalizedHeaders.indexOf(normalizeHeader(alias));
    if (index >= 0) return index;
  }
  return -1;
}

function summarizeDmbtRows(headers, rowsAfterHeader) {
  const deviceIndex = headerIndex(headers, headerAliasesByRole.DMBT_LOG.ma_thiet_bi);
  const detectedIndex = headerIndex(headers, headerAliasesByRole.DMBT_LOG.ngay_phat_hien);
  const repairedIndex = headerIndex(headers, headerAliasesByRole.DMBT_LOG.ngay_sua_chua);
  const validRows = rowsAfterHeader.filter((row) => {
    const deviceCode = String(row[deviceIndex] || "").trim();
    const detectedDate = String(row[detectedIndex] || "").trim();
    return deviceCode !== "" && detectedDate !== "";
  });
  const repairedRows = validRows.filter((row) => String(row[repairedIndex] || "").trim() !== "");
  return {
    total_data_rows: rowsAfterHeader.length,
    valid_rows: validRows.length,
    skipped_rows: Math.max(0, rowsAfterHeader.length - validRows.length),
    repaired_rows: repairedRows.length,
    pending_rows: Math.max(0, validRows.length - repairedRows.length),
  };
}

function summarizeHgtRows(headers, rowsAfterHeader) {
  const deviceIndex = headerIndex(headers, headerAliasesByRole.HGT_CHECKS.ma_thiet_bi);
  const cycleIndex = headerIndex(headers, headerAliasesByRole.HGT_CHECKS.chu_ky_ngay);
  const latestIndex = headerIndex(headers, headerAliasesByRole.HGT_CHECKS.lan_gan_nhat);
  const validRows = rowsAfterHeader.filter((row) => {
    const deviceCode = String(row[deviceIndex] || "").trim();
    const cycle = Number.parseInt(String(row[cycleIndex] || "").trim(), 10);
    const latest = String(row[latestIndex] || "").trim();
    return deviceCode !== "" && Number.isFinite(cycle) && cycle > 0 && latest !== "";
  });
  return {
    total_data_rows: rowsAfterHeader.length,
    valid_rows: validRows.length,
    skipped_rows: Math.max(0, rowsAfterHeader.length - validRows.length),
  };
}

function summarizeRowsForSheet(kind, headers, rowsAfterHeader) {
  if (kind === "HGT_CHECKS_CANDIDATE") return summarizeHgtRows(headers, rowsAfterHeader);
  if (kind === "DMBT_LOG_CANDIDATE" || kind === "REPAIR_LOG_CANDIDATE") {
    return summarizeDmbtRows(headers, rowsAfterHeader);
  }
  return {
    total_data_rows: rowsAfterHeader.length,
    valid_rows: 0,
    skipped_rows: rowsAfterHeader.length,
  };
}

function classifySheetTitle(title) {
  const normalized = normalizeHeader(title);
  if (normalized.startsWith("dmbt")) return "DMBT_LOG_CANDIDATE";
  if (normalized.startsWith("sua chua")) return "REPAIR_LOG_CANDIDATE";
  if (normalized.includes("hgt")) return "HGT_CHECKS_CANDIDATE";
  return "UNKNOWN";
}

function readProperties(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Missing local.properties: ${filePath}`);
  }
  const properties = {};
  const content = fs.readFileSync(filePath, "utf8");
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const equalsIndex = line.indexOf("=");
    if (equalsIndex <= 0) continue;
    const key = line.slice(0, equalsIndex).trim();
    const value = line.slice(equalsIndex + 1).trim();
    properties[key] = value;
  }
  return properties;
}

function hasValue(properties, key) {
  return typeof properties[key] === "string" && properties[key].trim() !== "";
}

function buildRoleRows(properties) {
  return Object.entries(roleToProperty).map(([role, propertyName]) => {
    const sheetId = hasValue(properties, propertyName) ? properties[propertyName].trim() : "";
    return {
      role,
      configured: sheetId !== "",
      sheet_id: sheetId,
      title: null,
      header_status: sheetId === "" ? "NOT_CONFIGURED" : "PENDING",
      header_row: null,
      headers: [],
      row_stats: null,
      error: null,
    };
  });
}

async function requestJson(url, accessToken) {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });
  const bodyText = await response.text();
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${bodyText.slice(0, 180)}`);
  }
  return JSON.parse(bodyText);
}

async function requestAccessToken(properties) {
  if (
    hasValue(properties, "SHEETS_OAUTH_CLIENT_ID") &&
    hasValue(properties, "SHEETS_OAUTH_CLIENT_SECRET") &&
    hasValue(properties, "SHEETS_REFRESH_TOKEN")
  ) {
    const body = new URLSearchParams({
      client_id: properties.SHEETS_OAUTH_CLIENT_ID.trim(),
      client_secret: properties.SHEETS_OAUTH_CLIENT_SECRET.trim(),
      refresh_token: properties.SHEETS_REFRESH_TOKEN.trim(),
      grant_type: "refresh_token",
    });
    const response = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body,
    });
    const tokenResponse = await response.json();
    if (!response.ok || !tokenResponse.access_token) {
      throw new Error(`OAuth refresh failed with HTTP ${response.status}.`);
    }
    return tokenResponse.access_token;
  }
  if (hasValue(properties, "SHEETS_ACCESS_TOKEN")) {
    return properties.SHEETS_ACCESS_TOKEN.trim();
  }
  throw new Error("No Google Sheets auth is configured.");
}

function writeMarkdown(inventory, outputPath) {
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const lines = [
    "# Google Sheets Inventory",
    "",
    `- Generated: ${inventory.generated_at}`,
    `- Status: ${inventory.status}`,
    `- Online attempted: ${inventory.online.attempted}`,
    `- Row stats attempted: ${inventory.row_stats_attempted}`,
    "",
    "| Role | Configured | Sheet ID | Title | Header status | Header row | Row stats | Headers | Error |",
    "|---|---:|---:|---|---|---:|---|---|---|",
  ];
  for (const role of inventory.roles) {
    const rowStats = role.row_stats ? JSON.stringify(role.row_stats) : "";
    lines.push(
      `| ${role.role} | ${role.configured} | ${role.sheet_id} | ${role.title || ""} | ` +
        `${role.header_status} | ${role.header_row || ""} | ${rowStats} | ${role.headers.join(", ")} | ${role.error || ""} |`
    );
  }
  lines.push("");
  lines.push("Safety notes:");
  lines.push("- This inventory only reads metadata/header rows.");
  lines.push("- It never writes Google Sheets or local app data.");
  lines.push("- Secrets are intentionally excluded from this report.");
  lines.push("");
  lines.push("## All Spreadsheet Tabs");
  lines.push("");
  lines.push("| Sheet ID | Title | Mapped role / detected kind | Header row | Row stats |");
  lines.push("|---:|---|---|---:|---|");
  for (const sheet of inventory.all_sheets || []) {
    const rowStats = sheet.row_stats ? JSON.stringify(sheet.row_stats) : "";
    lines.push(
      `| ${sheet.sheet_id} | ${sheet.title} | ${sheet.mapped_role || sheet.detected_kind || ""} | ` +
        `${sheet.header_row || ""} | ${rowStats} |`
    );
  }
  lines.push("");
  lines.push("## Unmapped Tabs");
  lines.push("");
  if ((inventory.unmapped_sheets || []).length === 0) {
    lines.push("- None");
  } else {
    for (const sheet of inventory.unmapped_sheets) {
      lines.push(`- ${sheet.sheet_id}: ${sheet.title}`);
    }
  }
  fs.writeFileSync(outputPath, `${lines.join("\n")}\n`, "utf8");
}

async function main() {
  const args = parseArgs(process.argv);
  if (args.selfTest) {
    const detected = detectHeaderRow("DMBT_LOG", [
      ["DANH MUC THIET BI BAT THUONG NAM 2026"],
      ["STT", "Mã thiết bị", "Ngày phát hiện", "Ngày sửa chữa", "Ghi chú"],
    ]);
    if (detected.rowNumber !== 2 || !detected.headers.includes("Mã thiết bị")) {
      throw new Error("Header detection self-test failed.");
    }
    process.stdout.write("PASS\n");
    return;
  }

  const localPropertiesPath = path.join(args.projectRoot, "android-mvp", "local.properties");
  const properties = readProperties(localPropertiesPath);
  const spreadsheetId = hasValue(properties, "SHEETS_SPREADSHEET_ID")
    ? properties.SHEETS_SPREADSHEET_ID.trim()
    : "";
  if (!spreadsheetId) {
    throw new Error("SHEETS_SPREADSHEET_ID is missing.");
  }

  const roles = buildRoleRows(properties);
  const inventory = {
    status: "PASS",
    generated_at: new Date().toISOString(),
    spreadsheet_configured: true,
    roles,
    all_sheets: [],
    unmapped_sheets: [],
    row_stats_attempted: args.includeRowStats,
    online: {
      attempted: false,
      note: "Offline mode: only configured role mappings were inspected.",
    },
    output_path: args.outputPath || path.join(args.projectRoot, "docs", "sheets-inventory.md"),
  };

  if (!args.offline) {
    inventory.online.attempted = true;
    inventory.online.note = "Read spreadsheet metadata and configured header rows.";
    try {
      const accessToken = await requestAccessToken(properties);
      const metadataUrl =
        `https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(spreadsheetId)}` +
        "?fields=sheets(properties(sheetId,title))";
      const metadata = await requestJson(metadataUrl, accessToken);
      const titleBySheetId = new Map();
      for (const sheet of metadata.sheets || []) {
        titleBySheetId.set(String(sheet.properties.sheetId), String(sheet.properties.title));
      }
      const roleBySheetId = new Map(
        roles
          .filter((role) => role.configured)
          .map((role) => [String(role.sheet_id), role.role])
      );
      inventory.all_sheets = Array.from(titleBySheetId.entries()).map(([sheetId, title]) => ({
        sheet_id: sheetId,
        title,
        mapped_role: roleBySheetId.get(sheetId) || null,
        detected_kind: classifySheetTitle(title),
        header_row: null,
        headers: [],
        row_stats: null,
      }));
      inventory.unmapped_sheets = inventory.all_sheets.filter((sheet) => !sheet.mapped_role);

      for (const role of roles) {
        if (!role.configured) continue;
        const title = titleBySheetId.get(String(role.sheet_id));
        if (!title) {
          role.header_status = "MISSING_SHEET";
          role.error = "Configured sheetId was not found in spreadsheet metadata.";
          inventory.status = "WARN";
          continue;
        }
        role.title = title;
        const range = `${title}!1:20`;
        const valuesUrl =
          `https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(spreadsheetId)}` +
          `/values/${encodeURIComponent(range)}`;
        const values = await requestJson(valuesUrl, accessToken);
        const detected = detectHeaderRow(role.role, Array.isArray(values.values) ? values.values : []);
        role.headers = detected.headers;
        role.header_row = detected.rowNumber;
        role.header_status = role.headers.length > 0 ? "OK" : "HEADER_NOT_DETECTED";
        if (role.header_status !== "OK") inventory.status = "WARN";
        if (args.includeRowStats && role.header_row) {
          const allRowsRange = `${title}!A:ZZ`;
          const allRowsUrl =
            `https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(spreadsheetId)}` +
            `/values/${encodeURIComponent(allRowsRange)}`;
          const allRows = await requestJson(allRowsUrl, accessToken);
          const rows = Array.isArray(allRows.values) ? allRows.values : [];
          const rowsAfterHeader = rows.slice(role.header_row);
          const kind = role.role === "HGT_CHECKS" ? "HGT_CHECKS_CANDIDATE" : "DMBT_LOG_CANDIDATE";
          role.row_stats = summarizeRowsForSheet(kind, role.headers, rowsAfterHeader);
        }
      }

      for (const sheet of inventory.all_sheets) {
        const range = `${sheet.title}!1:20`;
        const valuesUrl =
          `https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(spreadsheetId)}` +
          `/values/${encodeURIComponent(range)}`;
        const values = await requestJson(valuesUrl, accessToken);
        const candidateRole = sheet.detected_kind === "HGT_CHECKS_CANDIDATE" ? "HGT_CHECKS" : "DMBT_LOG";
        const detected = detectHeaderRow(candidateRole, Array.isArray(values.values) ? values.values : []);
        sheet.header_row = detected.rowNumber;
        sheet.headers = detected.headers;
        if (args.includeRowStats && sheet.header_row) {
          const allRowsRange = `${sheet.title}!A:ZZ`;
          const allRowsUrl =
            `https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(spreadsheetId)}` +
            `/values/${encodeURIComponent(allRowsRange)}`;
          const allRows = await requestJson(allRowsUrl, accessToken);
          const rows = Array.isArray(allRows.values) ? allRows.values : [];
          const rowsAfterHeader = rows.slice(sheet.header_row);
          sheet.row_stats = summarizeRowsForSheet(sheet.detected_kind, sheet.headers, rowsAfterHeader);
        }
      }
    } catch (error) {
      inventory.status = "FAIL";
      inventory.online.note = `Online inventory failed without exposing secrets: ${error.message}`;
    }
  }

  writeMarkdown(inventory, inventory.output_path);

  if (args.json) {
    process.stdout.write(`${JSON.stringify(inventory, null, 2)}\n`);
  } else {
    process.stdout.write(`Sheets inventory status: ${inventory.status}\n`);
    process.stdout.write(`Report: ${inventory.output_path}\n`);
  }

  if (inventory.status === "FAIL") {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});
