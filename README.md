# YouTube Interest Analyzer based on linear space and cosine with Integral (Riemann) Decay

- [Introduction](#introduction)
- [Mathematical Model](#mathematical-model)
    - [What is Linear Space?](#about-linear-space)
    - [What are Riemann Integral Sums?](#about-riemann-integral)
    - [Category Space](#category-space)
    - [User Vector](#user-vector)
    - [Recommendations](#recommendations)
- [Project Structure](#project-structure)
    - [DataFlow](#data-flow)
    - [YouTubeAuth](#youtube-auth)
    - [YouTubeDataLoader](#youtube-data-loader)
    - [CategoryRegistry](#category-registry)
    - [Event](#event)
    - [MyVector](#my-vector)
    - [User](#user)


# Introduction
YouTube Interest Analyzer based on a geometric and integral approach 
where user and content are represented as vectors in a multi-dimensional category space. The system uses **integral decay (Riemann sum)** to account for temporal dynamics — older views lose relevance over time.

# Mathematical model
## About linear space
Linear space is a mathematical structure that represents 
a set of vectors for which the operation of addition to each other 
and multiplication by a scalar is defined.

(L, ℝ, +, ·)

### Why linear space?

1. **Categories as basis vectors** — each YouTube category is a basis vector
2. **User as linear combination** — user interests = sum of weighted categories
3. **Similarity as angle** — cosine between vectors shows interest closeness

## Riemann Integral Sums: Complete Introduction

---

### 1. Partition of an Interval

**Definition (Partition):** A **partition** of an interval $[a; b]$ is an arbitrary finite set $\tau$ of distinct points:

$$
a = x_0 < x_1 < x_2 < \dots < x_{n-1} < x_n = b
$$

The numbers $x_0, x_1, \dots, x_n$ are called **partition points**, and the intervals $[x_0; x_1], [x_1; x_2], \dots, [x_{n-1}; x_n]$ are called **partition subintervals**.

This set of points is $\tau $

The length of the $i$-th subinterval is denoted by:

$$
\Delta x_i = x_i - x_{i-1}, \quad i = 1, 2, \dots, n
$$

**Definition (Mesh/Rank):** The largest of the numbers $\Delta x_i$ is called the **mesh** or **rank** of the partition $\tau$ and is denoted by $\lambda(\tau)$:

$$
\lambda(\tau) = \max\{\Delta x_1, \Delta x_2, \dots, \Delta x_n\}
$$

**Example:**

For the interval $[0; 10]$ and partition $\tau: 0 < 2 < 5 < 7 < 10$:

$$
\Delta x_1 = 2,\ \Delta x_2 = 3,\ \Delta x_3 = 2,\ \Delta x_4 = 3 \quad \Rightarrow \quad \lambda(\tau) = 3
$$

---

### 2. Tagged Partition 

Choosing a point $\xi_i$ in **each** subinterval $[x_{i-1}; x_i]$ of the partition of $[a; b]$ gives us a **partition with marked points** or a **tagged partition**.

If we denote by $\xi$ the set of chosen points:

$$
\xi = \{\xi_1, \xi_2, \dots, \xi_n\}
$$

where $\xi_i \in [x_{i-1}; x_i]$ for each $i = 1, 2, \dots, n$, then the tagged partition is denoted by $(\tau, \xi)$.

The **rank** of a tagged partition $(\tau, \xi)$ is understood as the rank of the partition $\tau$:

$$
\lambda(\tau, \xi) = \lambda(\tau)
$$

---
### 3. Integral Sums

**Definition:** Let a function $f$ be defined on the interval $[a; b]$. For an arbitrary tagged partition $(\tau, \xi)$ of the interval $[a; b]$, where:

$$
\tau: a = x_0 < x_1 < x_2 < \dots < x_{n-1} < x_n = b
$$

$$
\xi: \xi_1, \xi_2, \dots, \xi_n, \quad \xi_i \in [x_{i-1}, x_i]
$$

we define the **integral sum** $S_f(\tau, \xi)$ by the formula:

$$
S_f(\tau, \xi) = \sum_{i=1}^{n} f(\xi_i) \, \Delta x_i
$$

where $\Delta x_i = x_i - x_{i-1}$.

**Geometric meaning:** Sum of areas of rectangles with:
- Height = $f(\xi_i)$
- Width = $\Delta x_i$

---

## Category Space

### 1. YouTube Category Mapping

Each YouTube video belongs to one of 17 content categories. The system uses the following mapping:

| ID | Category Name | Index |
|----|--------------|-------|
| 1 | Film & Animation | 0 |
| 2 | Autos & Vehicles | 1 |
| 10 | Music | 2 |
| 15 | Pets & Animals | 3 |
| 17 | Sports | 4 |
| 18 | Short Movies | 5 |
| 19 | Travel & Events | 6 |
| 20 | Gaming | 7 |
| 21 | Videoblogging | 8 |
| 22 | People & Blogs | 9 |
| 23 | Comedy | 10 |
| 24 | Entertainment | 11 |
| 25 | News & Politics | 12 |
| 26 | Howto & Style | 13 |
| 27 | Education | 14 |
| 28 | Science & Technology | 15 |
| 29 | Nonprofits & Activism | 16 |

---

### 2.  Category as Basis Vector

Each category is represented as a **standard basis vector** in $\mathbb{R}^{17}$:

$$
\mathbf{e}_i = (0, 0, \dots, 1, \dots, 0)
$$

where the $1$ is at position $i$ (the index of the category), and all other coordinates are $0$.

**Examples:**

$$
\mathbf{e}_{\text{Music}} = (0, 0, 1, 0, \dots, 0)
$$

$$
\mathbf{e}_{\text{Gaming}} = (0, 0, 0, 0, 0, 0, 0, 1, 0, \dots, 0)
$$

$$
\mathbf{e}_{\text{Education}} = (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0)
$$

---

### 3. Linear Independence

The set of all category basis vectors $\{\mathbf{e}_1, \mathbf{e}_2, \dots, \mathbf{e}_{17}\}$ is **linearly independent**:

$$
\alpha_1 \mathbf{e}_1 + \alpha_2 \mathbf{e}_2 + \dots + \alpha_{17} \mathbf{e}_{17} = \mathbf{0} \;\Rightarrow\; \alpha_1 = \alpha_2 = \dots = \alpha_{17} = 0
$$

---

### 4. User Vector

#### The Decay Formula

For each category, we compute its weight $V(t)$ using a **two-layer exponential decay** model:

$$
V(t) = V(t-1) \cdot \lambda + \big( D(t) \cdot \lambda^{\text{age}} \big)
$$

where:
- $V(t)$ — category weight at day $t$
- $\lambda = 0.95$ — decay coefficient (-5% every day)
- $D(t)$ — viewing dynamics at day $t$
- $\text{age} = T - t_k$ — age of the view in days
- $T$ — today's date
- $t_k$ — date of the view

#### Viewing Dynamics

The dynamics $D(t)$ represent the **session effect** — consecutive views amplify each other:

$$
D(t) = D(t-1) \cdot \lambda + W(t)
$$

where $W(t)$ is the total watch time (in minutes) for the category on day $t$.

#### Complete Algorithm in Code

```java
public void calculateWithDecayAndDynamics(double lambda) {
    LocalDate today = LocalDate.now();
    LocalDate cutoffDate = today.minusDays(MAX_DAYS);
    MyVector result = MyVector.zero(dimension);
    
    Map<Integer, TreeMap<LocalDate, Integer>> categoryByDay = groupEventsByDay();
    
    for (var entry : categoryByDay.entrySet()) {
        int categoryId = entry.getKey();
        TreeMap<LocalDate, Integer> days = entry.getValue();
        
        double dynamic = 0.0;  // D(t)
        double total = 0.0;    // V(t)
        
        for (LocalDate date = firstDay; !date.isAfter(today); date = date.plusDays(1)) {
            // Apply decay
            total = total * lambda;      // V(t) = V(t-1) * lambda
            dynamic = dynamic * lambda;  // D(t) = D(t-1) * lambda
            
            Integer watchTime = days.get(date);
            if (watchTime != null) {
                dynamic = dynamic + watchTime;  // D(t) += W(t)
                
                long age = ChronoUnit.DAYS.between(date, today);
                double decay = Math.pow(lambda, age);
                double contribution = decay * dynamic;  // D(t) * lambda^age
                
                total += contribution;  // V(t) += contribution
            }
        }
        
        result.set(categoryIndex, total);
    }
    
    this.userVector = result;
}
```

---

# Project Structure

### Data Flow

| Step | Component |
|------|-----------|
| 1 | YouTube API |
| 2 | PlaylistItem | 
| 3 | Video Details |
| 4 | Event |
| 5 | Recommendation Engine |
---

## YoutubeAuth

### Overview

The `YouTubeAuth` class handles OAuth 2.0 authorization for YouTube API access. It implements the **Desktop Application flow** (Installed App), which opens a browser window for the user to log in and grant permissions.

---

### Class Structure

#### Constants

| Constant | Purpose |
|----------|---------|
| `CLIENT_SECRETS_PATH` | Path to the OAuth client secrets JSON file. Stores application credentials from Google Cloud Console |
| `JSON_FACTORY` | Factory for JSON parsing (Jackson) |
| `SCOPES` | List of permissions required from the user during authorization |

### Scopes Used

```java
private static final List<String> SCOPES = List.of(
    "https://www.googleapis.com/auth/youtube.readonly",
    "https://www.googleapis.com/auth/youtube.force-ssl"
);
```

---

### Authentication Flow

The `authenticate()` method executes the following steps:

1. **HTTP Transport Setup** - Creates a trusted `NetHttpTransport` instance for API communication
2. **Credentials Loading** - Reads OAuth client secrets from `client-secret.json` in classpath resources
3. **Authorization Flow Creation** - Builds a `GoogleAuthorizationCodeFlow` with required permissions
4. **Local Server Setup** - Spins up a `LocalServerReceiver` on port 8888 to catch the OAuth callback
5. **User Authorization** - Launches browser, waits for user login/permission, exchanges code for tokens
6. **YouTube Service** - Constructs and returns an authenticated `YouTube` API client

---

### Dependencies

- Google OAuth Client Libraries
- Jetty HTTP Server (for local callback receiver)
- Jackson JSON Processor

---

## YouTubeDataLoader

### Overview

The YouTubeDataLoader class is responsible for fetching user interaction data from the YouTube API and converting it into standardized Event objects for the recommendation system. It serves as the bridge between YouTube's raw API responses and the system's internal data model.

| Field | Type                                                            | Purpose                                                  |
|----------|-----------------------------------------------------------------|----------------------------------------------------------|
| `youtube` | YouTube                                                         | Authenticated YouTube API client instance |
| `categoryRegistry` |         CategoryRegistry                 | Mapping utility for YouTube category IDs to human-readable names

---

### Fetch Liked Videos

```java
public List<Event> fetchLikedVideos(int maxEvents) throws IOException { }
```

1. **Playlist access** - Accessible special playlist "LL" (liked videos)
2. **MaxResult** - Iterates through API response pages (50 items per page)
3. **Video Metadata Extraction** - For each liked video: Video ID, title, Like timestamp (when the user liked the video)
4. **Detailed Video Info** - Calls getVideoDetails() for additional metadata: Category ID, Category name, Video duration (converted from ISO format to minutes)
5. **Event Creation** - Creates an Event object with: Like date (as LocalDate), Category ID, Watch time (video duration in minutes)

--- 

### Duration Parsing

Converts YouTube's ISO 8601 duration format to minutes.

#### Format Examples:

1. "PT5M30S" → 5.5 minutes
2. "PT1H2M10S" → 62.167 minutes
3. "PT45S" → 0.75 minutes
Algorithm:

Strips the "PT" prefix
Parses hours (H), minutes (M), and seconds (S) components
Converts to decimal minutes

---

### Recommend Video

Purpose: Searches and displays recommended videos based on top user interests.

#### Process:

Iterates through top category recommendations (sorted by weight)
For each category, calculates the number of videos to fetch:
- count = weight × 10
- Performs a YouTube search for videos in that category
- Prints video titles and URLs to console
