package main

import (
	"encoding/json"
	"fmt"
	"log"
	"math/rand/v2"
	"net/http"
	"sync/atomic"
	"time"
)

var processing = atomic.Bool{}

// Transaction represents a transaction with gasPrice and fee
type Transaction struct {
	ID       string `json:"id"`
	GasPrice int    `json:"gasPrice"`
	Fee      int    `json:"fee"`
}

type BlockRequest struct {
	Transactions []Transaction `json:"transactions"`
}

func getCurrentPriceHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	randomGasPrice := rand.IntN(1000) // random gasPrice up to 999
	if randomGasPrice < 100 {
		randomGasPrice += 100 // ensure gasPrice is at least 100
	}
	randomFee := rand.IntN(100) // random fee up to 99
	if randomFee < 10 {
		randomFee += 10 // ensure fee is at least 10
	}

	resp := map[string]int{
		"gasPrice": randomGasPrice,
		"fee":      randomFee,
	}
	if err := json.NewEncoder(w).Encode(resp); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
}

func simulateBlockHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	if processing.Load() {
		http.Error(w, "Processing another request", http.StatusServiceUnavailable)
		return
	}

	processing.Store(true)
	defer processing.Store(false)

	// Simulate random processing time (1-3 seconds)
	milliseconds := time.Duration(rand.IntN(3000))
	if milliseconds < 1000 {
		milliseconds += 1000
	}
	sleepDuration := milliseconds * time.Millisecond
	time.Sleep(sleepDuration)

	var transactions BlockRequest
	if err := json.NewDecoder(r.Body).Decode(&transactions); err != nil {
		http.Error(w, "Invalid request payload", http.StatusBadRequest)
		return
	}

	// For demonstration, let's assume our gas limit is 10,000
	gasLimit := 10000

	var totalGas, totalFees int
	for _, tx := range transactions.Transactions {
		totalGas += tx.GasPrice
		totalFees += tx.Fee
	}

	isBelowLimit := totalGas <= gasLimit
	if !isBelowLimit {
		http.Error(w, "Gas limit exceeded", http.StatusForbidden)
		return
	}

	resp := map[string]interface{}{
		"transactions":          transactions,
		"totalGas":              totalGas,
		"totalFees":             totalFees,
		"gasLimit":              gasLimit,
		"processingTimeSeconds": sleepDuration.Seconds(),
	}

	fmt.Println("Block processed successfully")
	fmt.Println("Total gas:", totalGas)
	fmt.Println("Total fees:", totalFees)
	fmt.Println("Gas limit:", gasLimit)
	fmt.Println("Processing time:", sleepDuration.Seconds(), "seconds")
	fmt.Println()

	if err := json.NewEncoder(w).Encode(resp); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
}

func main() {
	http.HandleFunc("/getCurrentPrice", getCurrentPriceHandler)
	http.HandleFunc("/simulateBlock", simulateBlockHandler)

	log.Println("Starting server on :8080...")
	if err := http.ListenAndServe(":8080", nil); err != nil {
		log.Fatal(err)
	}
}
