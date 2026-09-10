using System.Globalization;
using Dorado.Application.Interfaces;
using Dorado.Application.Models;
using Dorado.Application.Services;
using Dorado.Domain.Enums;
using Dorado.Domain.Models;

Thread.CurrentThread.CurrentCulture = CultureInfo.InvariantCulture;

static Guid G(int n) => new Guid($"00000000-0000-0000-0000-{n:000000000000}");

var engine = new SyncEngine();

// ---------------------------------------------------------------- inputs
List<Track> Tracks() => new()
{
    new Track { Id = G(1), Title = "Delta",   ArtistName = "A", AlbumTitle = "Al", Duration = TimeSpan.FromSeconds(1), FilePath = "/t1", Rating = HeartRating.Favorite },
    new Track { Id = G(2), Title = "Alpha",   ArtistName = "B", AlbumTitle = "Al", Duration = TimeSpan.FromSeconds(2), FilePath = "/t2", Rating = HeartRating.None },
    new Track { Id = G(3), Title = "Charlie", ArtistName = "C", AlbumTitle = "Al", Duration = TimeSpan.FromSeconds(3), FilePath = "/t3", Rating = HeartRating.Favorite },
    new Track { Id = G(4), Title = "Bravo",   ArtistName = "D", AlbumTitle = "Al", Duration = TimeSpan.FromSeconds(4), FilePath = "/t4", Rating = HeartRating.Dislike },
    new Track { Id = G(5), Title = "Echo",    ArtistName = "E", AlbumTitle = "Al", Duration = TimeSpan.FromSeconds(5), FilePath = "/t5", Rating = HeartRating.Favorite },
};

List<PodcastEpisode> Episodes() => new()
{
    new PodcastEpisode { Id = G(11), Title = "Ep Old",    SeriesTitle = "S1", PublishedAtUtc = new DateTime(2020, 1, 1), Duration = TimeSpan.FromSeconds(1), AudioUrl = "/p1", IsPlayed = false },
    new PodcastEpisode { Id = G(12), Title = "Ep New",    SeriesTitle = "S1", PublishedAtUtc = new DateTime(2024, 1, 1), Duration = TimeSpan.FromSeconds(2), AudioUrl = "/p2", IsPlayed = false },
    new PodcastEpisode { Id = G(13), Title = "Ep Mid",    SeriesTitle = "S1", PublishedAtUtc = new DateTime(2022, 1, 1), Duration = TimeSpan.FromSeconds(3), AudioUrl = "/p3", IsPlayed = false },
    new PodcastEpisode { Id = G(14), Title = "Ep Played", SeriesTitle = "S1", PublishedAtUtc = new DateTime(2025, 1, 1), Duration = TimeSpan.FromSeconds(4), AudioUrl = "/p4", IsPlayed = true },
};

List<Video> Videos() => new()
{
    new Video { Id = G(21), Title = "Vid A", FilePath = "/v1", SizeBytes = 100000, AddedAtUtc = new DateTime(2021, 1, 1) },
    new Video { Id = G(22), Title = "Vid B", FilePath = "/v2", SizeBytes = 200000, AddedAtUtc = new DateTime(2023, 1, 1) },
};

List<Photo> Photos() => new()
{
    new Photo { Id = G(31), Title = "Pho A", FolderPath = "F1", FilePath = "/ph1", SizeBytes = 50000, AddedAtUtc = new DateTime(2020, 1, 1) },
    new Photo { Id = G(32), Title = "Pho B", FolderPath = "F1", FilePath = "/ph2", SizeBytes = 60000, AddedAtUtc = new DateTime(2022, 1, 1) },
    new Photo { Id = G(33), Title = "Pho C", FolderPath = "F2", FilePath = "/ph3", SizeBytes = 70000, AddedAtUtc = new DateTime(2024, 1, 1) },
};

SyncInput Input() => new()
{
    Tracks = Tracks(),
    Videos = Videos(),
    Photos = Photos(),
    PodcastEpisodes = Episodes(),
};

var device = new FixtureTransport
{
    TotalCapacityBytes = 300000,
    SystemBytes = 0,
    Items =
    {
        new DeviceContentItem { Category = SyncCategoryType.Music,    EntityId = G(1),  Title = "Delta",      DevicePath = "/d1", SizeBytes = 16000 },
        new DeviceContentItem { Category = SyncCategoryType.Music,    EntityId = G(99), Title = "Stale Song", DevicePath = "/d9", SizeBytes = 9000 },
        new DeviceContentItem { Category = SyncCategoryType.Pictures, EntityId = G(98), Title = "Stale Photo", DevicePath = "/d8", SizeBytes = 7000 },
        new DeviceContentItem { Category = SyncCategoryType.Videos,   EntityId = G(21), Title = "Vid A",      DevicePath = "/d2", SizeBytes = 100000 },
        new DeviceContentItem { Category = SyncCategoryType.Podcasts, EntityId = G(97), Title = "Stale Ep",   DevicePath = "/d7", SizeBytes = 5000 },
    },
};

AppSettings Settings() => new()
{
    MusicSyncRule = "Selected Favorites",
    PodcastSyncRule = "3 Newest Episodes",
    VideoSyncRule = "All Videos & Pictures",
    PicturesSyncRule = "Newest 25 Items",
};

void Print(string name, SyncGroup group, IDeviceTransport transport, SyncInput input)
{
    Console.WriteLine($"== {name} ==");
    Console.WriteLine($"group serial={group.DeviceSerialNumber} guest={group.IsGuestSession.ToString().ToLowerInvariant()}");
    var plan = engine.BuildPlan(group, input, transport);
    foreach (var item in plan.Items)
    {
        Console.WriteLine(
            $"ITEM {item.Action.ToString().ToUpperInvariant()} {item.Category.ToString().ToUpperInvariant()} " +
            $"{item.EntityId} bytes={item.SizeBytes} title={item.Title} detail={item.Detail ?? ""}");
    }
    Console.WriteLine(
        $"counts add={plan.AddCount} remove={plan.RemoveCount} keep={plan.KeepCount} " +
        $"addBytes={plan.TotalAddBytes} removeBytes={plan.TotalRemoveBytes}");
    Console.WriteLine();
}

// A — full plan (favorites, recency, newest, removes, keeps, capacity truncation)
Print("A-plan", engine.BuildDefaultGroup("SERIAL", Settings()), device, Input());

// B — guest session: add-only, never removes
Print("B-guest", engine.BuildDefaultGroup("SERIAL", Settings(), isGuestSession: true), device, Input());

// C — manual music: neither added nor removed
var manual = Settings();
manual.MusicSyncRule = "Manual";
Print("C-manual-music", engine.BuildDefaultGroup("SERIAL", manual), device, Input());

// D — default-group derivation from settings rule strings
Console.WriteLine("== D-rules ==");
void PrintRules(string label, AppSettings s)
{
    var g = engine.BuildDefaultGroup("SERIAL", s);
    foreach (var r in g.Categories)
    {
        var mode = r.Mode switch
        {
            SyncMode.Automatic => "AUTOMATIC",
            SyncMode.SelectedItems => "SELECTED",
            _ => "MANUAL",
        };
        Console.WriteLine(
            $"RULE {label} {r.Category.ToString().ToUpperInvariant()} mode={mode} " +
            $"newest={(r.NewestCount?.ToString() ?? "null")} favorite={r.PreferFavorites.ToString().ToLowerInvariant()} text={r.RuleText}");
    }
}
PrintRules("automatic", new AppSettings { MusicSyncRule = "All Music (Automatic Sync)", PodcastSyncRule = "All Unplayed Episodes", VideoSyncRule = "All Videos & Pictures", PicturesSyncRule = "All Pictures" });
PrintRules("selected", new AppSettings { MusicSyncRule = "Selected Items", PodcastSyncRule = "3 Newest Episodes", VideoSyncRule = "Selected Items", PicturesSyncRule = "5 Newest" });
PrintRules("manual", new AppSettings { MusicSyncRule = "Sync Manually", PodcastSyncRule = "Sync Manually", VideoSyncRule = "Nothing", PicturesSyncRule = "Newest 25 Items" });

class FixtureTransport : IDeviceTransport
{
    public string DeviceSerialNumber { get; set; } = "SERIAL";
    public string DeviceName { get; set; } = "Zune HD";
    public long TotalCapacityBytes { get; set; }
    public long SystemBytes { get; set; }
    public List<DeviceContentItem> Items { get; set; } = new();

    public IReadOnlyList<DeviceContentItem> GetContents() => Items;
    public bool TryGetItem(Guid entityId, out DeviceContentItem item)
    {
        var found = Items.FirstOrDefault(i => i.EntityId == entityId);
        item = found!;
        return found != null;
    }
    public void CopyToDevice(TransferItem item) { }
    public void RemoveFromDevice(DeviceContentItem item) { }
    public long UsedBytes => Items.Sum(i => i.SizeBytes);
    public long FreeBytes => TotalCapacityBytes - SystemBytes - UsedBytes;
}
